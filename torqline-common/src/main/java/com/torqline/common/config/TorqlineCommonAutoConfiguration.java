package com.torqline.common.config;

import com.torqline.common.events.Topics;
import com.torqline.common.messaging.IdempotencyGuard;
import com.torqline.common.messaging.OutboxRelay;
import com.torqline.common.messaging.OutboxWriter;
import com.torqline.common.web.ApiExceptionHandler;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.databind.ObjectMapper;

/** Wires the shared messaging and web plumbing into every Torqline service. */
@AutoConfiguration
@EnableScheduling
public class TorqlineCommonAutoConfiguration {

    @Bean
    OutboxWriter outboxWriter(JdbcTemplate jdbc, ObjectMapper mapper) {
        return new OutboxWriter(jdbc, mapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = "torqline.outbox", name = "relay-enabled", matchIfMissing = true)
    OutboxRelay outboxRelay(JdbcTemplate jdbc, PlatformTransactionManager txManager,
                            KafkaTemplate<String, String> kafka,
                            @Value("${torqline.outbox.batch-size:100}") int batchSize) {
        return new OutboxRelay(jdbc, new TransactionTemplate(txManager), kafka, batchSize);
    }

    @Bean
    IdempotencyGuard idempotencyGuard(JdbcTemplate jdbc, PlatformTransactionManager txManager) {
        return new IdempotencyGuard(jdbc, new TransactionTemplate(txManager));
    }

    @Bean
    KafkaAdmin.NewTopics torqlineTopics() {
        return new KafkaAdmin.NewTopics(Topics.ALL.stream()
                .map(name -> TopicBuilder.name(name).partitions(3).replicas(1).build())
                .toArray(NewTopic[]::new));
    }

    /** Retry a failing record 3 times, one second apart, then park it on {@code <topic>-dlt}. */
    @Bean
    CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafka) {
        return new DefaultErrorHandler(new DeadLetterPublishingRecoverer(kafka), new FixedBackOff(1000L, 3));
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    ApiExceptionHandler apiExceptionHandler() {
        return new ApiExceptionHandler();
    }
}
