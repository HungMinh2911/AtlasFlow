package com.atlasflow.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class CacheService {

    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT =
            new DefaultRedisScript<>(
                    "if redis.call('GET', KEYS[1]) == ARGV[1] " +
                            "then return redis.call('DEL', KEYS[1]) " +
                            "else return 0 end",
                    Long.class
            );

    private static final Logger logger =
            LoggerFactory.getLogger(CacheService.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Counter hitCounter;
    private final Counter missCounter;
    private final Counter readErrorCounter;
    private final Counter deserializeErrorCounter;
    private final Counter serializationErrorCounter;
    private final Counter writeErrorCounter;
    private final Counter invalidationErrorCounter;
    private final Timer readTimer;
    private final Timer writeTimer;
    private final Timer evictTimer;

    public CacheService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.hitCounter = meterRegistry.counter("atlasflow.cache.hit");
        this.missCounter = meterRegistry.counter("atlasflow.cache.miss");
        this.readErrorCounter = meterRegistry.counter("atlasflow.cache.read_error");
        this.deserializeErrorCounter = meterRegistry.counter("atlasflow.cache.deserialize_error");
        this.serializationErrorCounter = meterRegistry.counter("atlasflow.cache.serialization_error");
        this.writeErrorCounter = meterRegistry.counter("atlasflow.cache.write_error");
        this.invalidationErrorCounter = meterRegistry.counter("atlasflow.cache.invalidation_error");
        this.readTimer = meterRegistry.timer("atlasflow.cache.read");
        this.writeTimer = meterRegistry.timer("atlasflow.cache.write");
        this.evictTimer = meterRegistry.timer("atlasflow.cache.evict");
    }

    public <T> Optional<T> get(String key, Class<T> valueType) {
        Timer.Sample sample = Timer.start();
        try {
            String json = redisTemplate
                    .opsForValue()
                    .get(key);

            if (json == null) {
                missCounter.increment();
                return Optional.empty();
            }

            T value = objectMapper.readValue(json, valueType);
            hitCounter.increment();
            return Optional.of(value);
        } catch (DataAccessException exception) {
            readErrorCounter.increment();
            logger.warn(
                    "Cache read failed for key {}: {}",
                    key,
                    exception.getMessage()
            );
            return Optional.empty();
        } catch (JsonProcessingException exception) {
            deserializeErrorCounter.increment();
            logger.warn(
                    "Cached value is invalid; evicting key {}: {}",
                    key,
                    exception.getMessage()
            );
            evict(key);
            return Optional.empty();
        } finally {
            sample.stop(readTimer);
        }
    }

    public void put(String key, Object value, Duration ttl) {
        Timer.Sample sample = Timer.start();
        try {
            String json = objectMapper.writeValueAsString(value);

            redisTemplate
                    .opsForValue()
                    .set(key, json, ttl);
        } catch (DataAccessException exception) {
            writeErrorCounter.increment();
            logger.warn(
                    "Cache write failed for key {}: {}",
                    key,
                    exception.getMessage()
            );
        } catch (JsonProcessingException exception) {
            serializationErrorCounter.increment();
            logger.warn(
                    "Cache value serialization failed for key {}: {}",
                    key,
                    exception.getMessage()
            );
        } finally {
            sample.stop(writeTimer);
        }
    }

    public void evict(String key) {
        Timer.Sample sample = Timer.start();
        try {
            redisTemplate.delete(key);
        } catch (DataAccessException exception) {
            invalidationErrorCounter.increment();
            logger.warn(
                    "Cache invalidation failed for key {}: {}",
                    key,
                    exception.getMessage()
            );
        } finally {
            sample.stop(evictTimer);
        }
    }

    public Optional<String> tryAcquireLock(
            String lockKey,
            Duration lease
    ) {
        String token = UUID.randomUUID().toString();

        try {
            Boolean acquired = redisTemplate
                    .opsForValue()
                    .setIfAbsent(lockKey, token, lease);

            return Boolean.TRUE.equals(acquired)
                    ? Optional.of(token)
                    : Optional.empty();
        } catch (DataAccessException exception) {
            logger.warn(
                    "Cache lock acquisition failed for key {}: {}",
                    lockKey,
                    exception.getMessage()
            );
            return Optional.empty();
        }
    }

    public void releaseLock(String lockKey, String token) {
        try {
            redisTemplate.execute(
                    RELEASE_LOCK_SCRIPT,
                    List.of(lockKey),
                    token
            );
        } catch (DataAccessException exception) {
            logger.warn(
                    "Cache lock release failed for key {}: {}",
                    lockKey,
                    exception.getMessage()
            );
        }
    }
}
