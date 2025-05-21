package com.projectoracle.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for the application's caching system.
 * Uses Caffeine as the default cache provider with configurable TTL and size limits.
 * Optionally supports Redis for distributed deployments when spring.cache.redis.enabled=true.
 */
@Configuration
@EnableCaching
public class CacheConfig {
    private static final Logger logger = LoggerFactory.getLogger(CacheConfig.class);
    
    @Value("${spring.cache.caffeine.spec:maximumSize=500,expireAfterAccess=600s}")
    private String caffeineSpec;
    
    @Value("${spring.cache.cache-names:tests,elements,suggestions,apiEndpoints,testCases,testSuggestions}")
    private String[] cacheNames;
    
    @Value("${spring.cache.redis.enabled:false}")
    private boolean redisEnabled;
    
    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;
    
    @Value("${spring.data.redis.port:6379}")
    private int redisPort;
    
    @Value("${spring.data.redis.password:}")
    private String redisPassword;
    
    @Value("${spring.data.redis.ssl.enabled:false}")
    private boolean redisSslEnabled;
    
    @Value("${spring.cache.redis.time-to-live:600000}")
    private long redisTimeToLive;
    
    /**
     * Configures and returns the Caffeine cache manager.
     * This is the default cache manager when Redis is not enabled.
     * 
     * @return the Caffeine cache manager with configured caches
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "spring.cache.redis.enabled", havingValue = "false", matchIfMissing = true)
    public CacheManager caffeineCacheManager() {
        logger.info("Configuring Caffeine cache manager with spec: {}", caffeineSpec);
        
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        
        // Set cache names from properties
        cacheManager.setCacheNames(Arrays.asList(cacheNames));
        
        // Use the caffeine spec from application.properties
        cacheManager.setCaffeineSpec(com.github.benmanes.caffeine.cache.CaffeineSpec.parse(caffeineSpec));
        
        // Note: We don't need to set Caffeine builder here as we're using the spec
        // which already includes recordStats if specified in the properties
        
        logger.info("Caffeine cache manager configured with {} caches: {}", 
                cacheNames.length, String.join(", ", cacheNames));
        
        return cacheManager;
    }
    
    /**
     * Creates a Redis connection factory.
     * This bean is only created when Redis is enabled.
     * 
     * @return the Redis connection factory
     */
    @Bean
    @ConditionalOnProperty(name = "spring.cache.redis.enabled", havingValue = "true")
    public RedisConnectionFactory redisConnectionFactory() {
        logger.info("Configuring Redis connection factory for {}:{} with SSL={}", redisHost, redisPort, redisSslEnabled);
        
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration();
        redisConfig.setHostName(redisHost);
        redisConfig.setPort(redisPort);
        
        if (redisPassword != null && !redisPassword.isEmpty()) {
            redisConfig.setPassword(redisPassword);
        }
        
        // Configure SSL if enabled
        if (redisSslEnabled) {
            org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration clientConfig = 
                org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration.builder()
                .useSsl().build();
            return new LettuceConnectionFactory(redisConfig, clientConfig);
        } else {
            return new LettuceConnectionFactory(redisConfig);
        }
    }
    
    /**
     * Configures and returns the Redis cache manager.
     * This is used when Redis is enabled for distributed caching.
     * 
     * @param connectionFactory the Redis connection factory
     * @return the Redis cache manager with configured caches
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "spring.cache.redis.enabled", havingValue = "true")
    public CacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
        logger.info("Configuring Redis cache manager with TTL: {}ms", redisTimeToLive);
        
        // Configure default serialization
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMillis(redisTimeToLive))
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new GenericJackson2JsonRedisSerializer()));
        
        // Create cache configurations for each cache with the same TTL
        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();
        for (String cacheName : cacheNames) {
            cacheConfigs.put(cacheName, defaultConfig);
        }
        
        RedisCacheManager cacheManager = RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigs)
            .build();
        
        logger.info("Redis cache manager configured with {} caches: {}", 
                cacheNames.length, String.join(", ", cacheNames));
        
        return cacheManager;
    }
}