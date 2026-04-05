package com.ansj.shoppayment.config;

import com.ansj.shoppayment.config.KafkaConsumerAwareRebalanceListener;
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
        config.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(AdminClientConfig.RETRIES_CONFIG, 5);
        config.put(AdminClientConfig.RETRY_BACKOFF_MS_CONFIG, 1000);
        return new KafkaAdmin(config);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            KafkaConsumerAwareRebalanceListener rebalanceListener) {

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);

        ContainerProperties containerProperties = factory.getContainerProperties();
        containerProperties.setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        containerProperties.setConsumerRebalanceListener(rebalanceListener);

        log.info("Set AckMode: {}", containerProperties.getAckMode());
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
