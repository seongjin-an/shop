package com.ansj.shopstock.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.producer.Producer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
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

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            KafkaConsumerAwareRebalanceListener rebalanceListener) {

        ConcurrentKafkaListenerContainerFactory<String, String> containerFactory = new ConcurrentKafkaListenerContainerFactory<>();
        containerFactory.setConsumerFactory(consumerFactory);

        ContainerProperties containerProperties = containerFactory.getContainerProperties();
        containerProperties.setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE); // 수동 커밋 처리
        containerProperties.setConsumerRebalanceListener(rebalanceListener); // 내가 만든 Rebalance 빈

        // @Retryable(maxAttempts=5) 이 모두 소진된 뒤에도 실패하면 여기서 잡아 오프셋을 커밋한다.
        // 2초 간격으로 최대 2회 재시도(총 3번 시도) → 그래도 실패하면 에러 로그 후 메시지를 건너뜀(무한 루프 방지).
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                (record, exception) -> log.error(
                        "[ErrorHandler] 최종 처리 실패 — 메시지를 건너뜁니다. " +
                        "topic={}, partition={}, offset={}, cause={}",
                        record.topic(), record.partition(), record.offset(), exception.getMessage()),
                new FixedBackOff(2_000L, 2L)
        );
        containerFactory.setCommonErrorHandler(errorHandler);

        log.info("Set AckMode: {}", containerProperties.getAckMode());
        return containerFactory;
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate(DefaultKafkaProducerFactory<String, String> producerFactory) {
        Producer<String, String> producer = producerFactory.createProducer();
        producer.close();
        log.info("Kafka producer initialize");
        return new KafkaTemplate<>(producerFactory);
    }
}
