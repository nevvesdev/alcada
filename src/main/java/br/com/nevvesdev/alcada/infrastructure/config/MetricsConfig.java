package br.com.nevvesdev.alcada.infrastructure.config;

import br.com.nevvesdev.alcada.infrastructure.metrics.MeteredOutboxMessagePublisher;
import br.com.nevvesdev.alcada.infrastructure.metrics.OutboxMetrics;
import br.com.nevvesdev.alcada.infrastructure.outbox.LoggingOutboxMessagePublisher;
import br.com.nevvesdev.alcada.infrastructure.outbox.OutboxMessagePublisher;
import br.com.nevvesdev.alcada.infrastructure.persistence.SpringDataOutboxEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration(proxyBeanMethods = false)
public class MetricsConfig {

    @Bean
    @Primary
    OutboxMessagePublisher meteredOutboxMessagePublisher(LoggingOutboxMessagePublisher delegate,
                                                         MeterRegistry meterRegistry) {
        return new MeteredOutboxMessagePublisher(delegate, meterRegistry);
    }

    @Bean
    OutboxMetrics outboxMetrics(SpringDataOutboxEventRepository repository, AlcadaProperties properties) {
        return new OutboxMetrics(repository, properties.outbox().maxAttempts());
    }
}