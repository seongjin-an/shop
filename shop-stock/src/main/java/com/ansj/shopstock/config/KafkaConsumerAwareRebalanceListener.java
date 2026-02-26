package com.ansj.shopstock.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.listener.ConsumerAwareRebalanceListener;
import org.springframework.stereotype.Component;

import java.util.Collection;

@Slf4j
@Component
public class KafkaConsumerAwareRebalanceListener implements ConsumerAwareRebalanceListener {

    @Override
    public void onPartitionsAssigned(Consumer<?, ?> consumer, Collection<TopicPartition> partitions) {
        //새 파티션 할당됨. 작업 재개.
        log.info("Kafka Consumer {} assigned: {}", consumer.toString(), partitions.toString());
    }

    @Override
    public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
        //파티션 회수됨. 마지막 오프셋 커밋 중...
        log.info("Kafka Consumer revoked: {}", partitions.toString());
    }
}
