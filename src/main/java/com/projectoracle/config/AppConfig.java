package com.projectoracle.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Application configuration for thread pools and resource management
 * to ensure efficient operation on consumer-grade hardware.
 */
@Configuration
public class AppConfig {

    /**
     * Configure async executor with resource-aware settings
     * Adapts to available CPU cores and manages thread resources efficiently
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        int corePoolSize = Math.max(2, availableProcessors / 2);
        int maxPoolSize = Math.max(4, availableProcessors - 1);

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("OracleExecutor-");
        executor.initialize();

        return executor;
    }

    /**
     * Configure a lighter executor for background tasks
     * Used for non-time-critical operations to reduce resource usage
     */
    @Bean(name = "backgroundExecutor")
    public Executor backgroundExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("OracleBackground-");
        executor.initialize();

        return executor;
    }
}