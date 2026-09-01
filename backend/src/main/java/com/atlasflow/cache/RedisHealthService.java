package com.atlasflow.cache;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnectionCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisHealthService {

    private final StringRedisTemplate redisTemplate;


    public RedisHealthService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isAvailable() {
        try {
            String response = redisTemplate.execute(
                    RedisConnectionCommands::ping
            );
            return "PONG".equalsIgnoreCase(response);
        } catch (DataAccessException e) {
            return false;
        }
    }
}
