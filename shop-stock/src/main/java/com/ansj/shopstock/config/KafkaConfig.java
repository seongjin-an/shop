package com.ansj.shopstock.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> config = new HashMap<>();
        config.put("bootstrap.servers", bootstrapServers);

        config.put(AdminClientConfig.RETRIES_CONFIG, 5); // 실패 시 재시도 횟수
        config.put(AdminClientConfig.RETRY_BACKOFF_MS_CONFIG, 1000); // 실패 시 재시도 하기 전 1초 지연
        return new KafkaAdmin(config);
    }

    /**
     * 메인 컨슈머 팩토리.
     * - AckMode.MANUAL_IMMEDIATE: 처리 성공 시에만 수동 ack → 실패 시 DefaultErrorHandler 로 위임
     * - DefaultErrorHandler: 2초 간격 2회 재시도(총 3번 시도) 후에도 실패하면 .DLT 토픽으로 라우팅
     *   → 무한 루프 없이 메시지 보존 (DLT 컨슈머가 이후 재처리)
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            KafkaConsumerAwareRebalanceListener rebalanceListener,
            KafkaTemplate<String, String> kafkaTemplate) {

        ConcurrentKafkaListenerContainerFactory<String, String> containerFactory = new ConcurrentKafkaListenerContainerFactory<>();
        containerFactory.setConsumerFactory(consumerFactory);

        ContainerProperties containerProperties = containerFactory.getContainerProperties();
        containerProperties.setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        containerProperties.setConsumerRebalanceListener(rebalanceListener);

        // 최종 실패 메시지를 버리지 않고 {원본토픽}.DLT 파티션 0 으로 발행
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> {
                    log.error("[DLT] 최종 처리 실패 — DLT로 라우팅. topic={}, partition={}, offset={}, cause={}",
                            record.topic(), record.partition(), record.offset(), ex.getMessage());
                    return new TopicPartition(record.topic() + "-DLT", 0);
                });

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(2_000L, 2L));
        containerFactory.setCommonErrorHandler(errorHandler);

        log.info("Set AckMode: {}", containerProperties.getAckMode());
        return containerFactory;
    }

    /**
     * DLT 전용 컨슈머 팩토리.
     * - AckMode.RECORD: 메시지 처리 완료(성공/실패 무관) 후 자동 커밋
     * - ErrorHandler 없음: DLT → 또 다른 DLT 로 이어지는 무한 루프 방지
     *   실패 시 로그만 남기고 수동 개입으로 처리
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> dltKafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        return factory;
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate(DefaultKafkaProducerFactory<String, String> producerFactory) {
        Producer<String, String> producer = producerFactory.createProducer();
        producer.close();
        log.info("Kafka producer initialize");
        return new KafkaTemplate<>(producerFactory);
    }
}
