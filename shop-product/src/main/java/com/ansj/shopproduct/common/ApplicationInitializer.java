package com.ansj.shopproduct.common;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaAdmin;

@Slf4j
//@Configuration
public class ApplicationInitializer implements ApplicationRunner {

    private final KafkaAdmin kafkaAdmin;

    public ApplicationInitializer(KafkaAdmin kafkaAdmin) {
        this.kafkaAdmin = kafkaAdmin;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
//        try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
//            new NewTopic()
//        }
    }
}
