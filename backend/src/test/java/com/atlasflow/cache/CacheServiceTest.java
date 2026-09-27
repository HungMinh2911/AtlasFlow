package com.atlasflow.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CacheServiceTest {

    private static final String KEY = "atlasflow:project:test:v1";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private CacheService cacheService;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        cacheService = new CacheService(
                redisTemplate,
                new ObjectMapper(),
                meterRegistry
        );
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void getShouldCountMissWhenKeyDoesNotExist() {
        when(valueOperations.get(KEY)).thenReturn(null);

        Optional<String> result = cacheService.get(KEY, String.class);

        assertTrue(result.isEmpty());
        assertEquals(1.0, meterRegistry.counter("atlasflow.cache.miss").count());
    }

    @Test
    void getShouldFallbackWhenRedisReadFails() {
        when(valueOperations.get(KEY))
                .thenThrow(new DataAccessResourceFailureException("Redis unavailable"));

        Optional<String> result = cacheService.get(KEY, String.class);

        assertTrue(result.isEmpty());
        assertEquals(1.0, meterRegistry.counter("atlasflow.cache.read_error").count());
    }

    @Test
    void getShouldEvictInvalidCachedJsonAndFallback() {
        when(valueOperations.get(KEY)).thenReturn("not-valid-json");

        Optional<String> result = cacheService.get(KEY, String.class);

        assertTrue(result.isEmpty());
        verify(redisTemplate).delete(KEY);
        assertEquals(
                1.0,
                meterRegistry.counter("atlasflow.cache.deserialize_error").count()
        );
    }
}
