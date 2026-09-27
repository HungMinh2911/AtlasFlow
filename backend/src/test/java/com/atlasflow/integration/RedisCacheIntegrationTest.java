package com.atlasflow.integration;

import com.atlasflow.cache.CacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedisCacheIntegrationTest extends AbstractBaseIntegrationTest {

    private static final String KEY = "atlasflow:test:real-redis:v1";

    @Autowired
    private CacheService cacheService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private MeterRegistry meterRegistry;

    @AfterEach
    void cleanRedisKey() {
        redisTemplate.delete(KEY);
    }

    @Test
    void realRedisShouldSupportMissPutAndHit() {
        double missBefore = meterRegistry
                .counter("atlasflow.cache.miss")
                .count();
        double hitBefore = meterRegistry
                .counter("atlasflow.cache.hit")
                .count();

        Optional<String> firstRead = cacheService.get(KEY, String.class);

        assertTrue(firstRead.isEmpty());
        assertFalse(redisTemplate.hasKey(KEY));

        cacheService.put(KEY, "real-redis-value", Duration.ofMinutes(1));

        assertTrue(redisTemplate.hasKey(KEY));
        assertTrue(redisTemplate.getExpire(KEY) > 0);

        Optional<String> secondRead = cacheService.get(KEY, String.class);

        assertTrue(secondRead.isPresent());
        assertTrue(secondRead.get().equals("real-redis-value"));
        assertTrue(meterRegistry.counter("atlasflow.cache.miss").count() > missBefore);
        assertTrue(meterRegistry.counter("atlasflow.cache.hit").count() > hitBefore);
    }

    @Test
    void realRedisShouldEvictInvalidJson() {
        redisTemplate.opsForValue().set(KEY, "not-valid-json");

        Optional<String> result = cacheService.get(KEY, String.class);

        assertTrue(result.isEmpty());
        assertFalse(redisTemplate.hasKey(KEY));
    }

    @Test
    void unavailableRedisShouldFallbackWithoutThrowing() {
        LettuceConnectionFactory unavailableFactory =
                new LettuceConnectionFactory("127.0.0.1", 1);
        unavailableFactory.afterPropertiesSet();

        try {
            StringRedisTemplate unavailableTemplate =
                    new StringRedisTemplate(unavailableFactory);
            CacheService unavailableCache = new CacheService(
                    unavailableTemplate,
                    new ObjectMapper(),
                    new SimpleMeterRegistry()
            );

            assertDoesNotThrow(() -> {
                Optional<String> result =
                        unavailableCache.get(KEY, String.class);
                assertTrue(result.isEmpty());
            });
        } finally {
            unavailableFactory.destroy();
        }
    }
}
