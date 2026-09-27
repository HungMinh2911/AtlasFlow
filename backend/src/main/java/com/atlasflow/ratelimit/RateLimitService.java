package com.atlasflow.ratelimit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RateLimitService {

    private static final String RATE_LIMIT_SCRIPT = """
            local current = redis.call('INCR', KEYS[1])
            
            if current == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            
            local ttl = redis.call('TTL', KEYS[1])
            
            return {current, ttl}
            """;
    private final StringRedisTemplate stringRedisTemplate;
    private final boolean failOpen;
    private final Counter allowedCounter;
    private final Counter rejectedCounter;
    private final Counter redisErrorCounter;

    public RateLimitService(
            StringRedisTemplate stringRedisTemplate,
            MeterRegistry meterRegistry,
            @Value("${app.rate-limit.fail-open:true}") boolean failOpen
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.failOpen = failOpen;
        this.allowedCounter = meterRegistry.counter("atlasflow.ratelimit.allowed");
        this.rejectedCounter = meterRegistry.counter("atlasflow.ratelimit.rejected");
        this.redisErrorCounter = meterRegistry.counter("atlasflow.ratelimit.redis_error");
    }

    public RateLimitDecision check (
            String key,
            long limit,
            long windowSeconds
    ) {
        try {
            DefaultRedisScript<List> script =
                    new DefaultRedisScript<>();

            script.setScriptText(RATE_LIMIT_SCRIPT);
            script.setResultType(List.class);

            List<?> result = stringRedisTemplate.execute(
                    script,
                    List.of(key),
                    String.valueOf(windowSeconds)
            );

            if (result == null || result.size() < 2) {
                throw new IllegalStateException(
                        "Redis rate-limit script returned no result"
                );
            }

            long currentCount = ((Number) result.get(0)).longValue();
            long ttl = ((Number) result.get(1)).longValue();
            boolean allowed = currentCount <= limit;

            if (allowed) {
                allowedCounter.increment();
            } else {
                rejectedCounter.increment();
            }

            return new RateLimitDecision(
                    allowed,
                    currentCount,
                    allowed ? 0 : Math.max(ttl, 1)
            );
        } catch (DataAccessException | IllegalStateException exception) {
            redisErrorCounter.increment();

            if (failOpen) {
                allowedCounter.increment();
                return new RateLimitDecision(true, 0, 0);
            }

            throw new RateLimitUnavailableException();
        }
    }

}
