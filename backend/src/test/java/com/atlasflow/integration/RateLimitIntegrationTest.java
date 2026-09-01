package com.atlasflow.integration;

import com.atlasflow.ratelimit.RateLimitDecision;
import com.atlasflow.ratelimit.RateLimitKeyFactory;
import com.atlasflow.ratelimit.RateLimitService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimitIntegrationTest extends AbstractBaseIntegrationTest {

    @Autowired
    private RateLimitService rateLimitService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private String key;

    @AfterEach
    void cleanRateLimitKey() {
        if (key != null) {
            redisTemplate.delete(key);
        }
    }

    @Test
    void loginShouldAllowFiveRequestsAndRejectTheSixth() {
        key = RateLimitKeyFactory.login(
                "127.0.0.1",
                "Owner@AtlasFlow.Local"
        );

        for (int attempt = 1; attempt <= 5; attempt++) {
            RateLimitDecision decision = rateLimitService.check(
                    key,
                    5,
                    60
            );

            assertTrue(decision.allowed());
            assertEquals(attempt, decision.currentCount());
        }

        RateLimitDecision rejected = rateLimitService.check(
                key,
                5,
                60
        );

        assertFalse(rejected.allowed());
        assertEquals(6, rejected.currentCount());
        assertTrue(rejected.retryAfterSeconds() > 0);
        assertTrue(redisTemplate.getExpire(key) > 0);
        assertFalse(key.contains("owner@atlasflow.local"));
    }

    @Test
    void submitJobShouldUseProjectScopedKeyWithThirtyRequestLimit() {
        UUID projectId = UUID.randomUUID();
        key = RateLimitKeyFactory.submitJob(projectId);

        for (int attempt = 1; attempt <= 30; attempt++) {
            RateLimitDecision decision = rateLimitService.check(
                    key,
                    30,
                    60
            );

            assertTrue(decision.allowed());
        }

        assertFalse(rateLimitService.check(key, 30, 60).allowed());
        assertTrue(redisTemplate.getExpire(key) > 0);
    }
}
