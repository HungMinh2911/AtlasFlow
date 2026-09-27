package com.atlasflow.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class CorsConfig {

    // Allowlist origin cụ thể - KHÔNG dùng "*" vì không thể kết hợp với
    // allowCredentials(true) (spec CORS cấm, Spring sẽ throw exception lúc runtime)
    @Value("${app.cors.allowed-origins:http://localhost:3000,https://app.atlasflow.com}")
    private List<String> allowlistOrigin;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(allowlistOrigin);

        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        configuration.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "Accept",
                "X-Requested-With",
                "X-Request-Id",
                "Idempotency-Key",
                "X-API-Key"
            ));

        // Header server được phép để JS phía frontend ĐỌC từ response.
        // Mặc định trình duyệt chỉ expose vài header cơ bản (Content-Type...),
        // nếu API có trả thêm header custom (vd access token mới) thì phải khai ở đây.
        configuration.setExposedHeaders(List.of("Authorization", "X-Request-Id", "Retry-After"));

        // true nếu có dùng cookie (vd refresh token httpOnly) kèm request cross-site.
        // Nếu chỉ dùng Bearer token qua header thì có thể đổi thành false.
        configuration.setAllowCredentials(true);

        // Cache kết quả preflight OPTIONS phía trình duyệt trong 1h,
        // giảm số lượng request OPTIONS lặp lại không cần thiết.
        configuration.setMaxAge(3600L);


        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
