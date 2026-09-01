package com.atlasflow.cache;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class CacheConfig {

    // class này tạo ra một “cái cổng” để code Java tương tác với Redis theo kiểu String, ví dụ lưu key/value dạng text vào cache.
    @Bean // StringRedisTemplate: một template Redis chuyên xử lý dữ liệu kiểu String
    public StringRedisTemplate atlasFlowRedisTemplate(
            RedisConnectionFactory connectionFactory // Spring sẽ inject factory kết nối Redis từ cấu hình của project.
    ) {
        return new StringRedisTemplate(connectionFactory); // khởi tạo template Redis dùng cùng connection factory.
    }
}
