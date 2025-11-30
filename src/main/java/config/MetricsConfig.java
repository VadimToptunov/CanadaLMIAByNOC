package config;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Configuration for application metrics using Micrometer.
 * 
 * This configuration enables:
 * - Custom business metrics (search operations, data processing, etc.)
 * - Method-level timing with @Timed annotation
 * - Prometheus metrics export
 */
@Configuration
@EnableAspectJAutoProxy
public class MetricsConfig {

    /**
     * Enables @Timed annotation for method-level timing metrics.
     */
    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }

}

