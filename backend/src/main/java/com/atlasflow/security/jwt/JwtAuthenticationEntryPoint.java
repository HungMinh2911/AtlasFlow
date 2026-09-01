package com.atlasflow.security.jwt;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;


/**
 * Được Spring Security gọi khi một request KHÔNG có / KHÔNG xác thực được
 * thông tin đăng nhập nhưng lại cố truy cập endpoint yêu cầu authentication
 * (tức là lỗi 401 Unauthorized - phân biệt với AccessDeniedHandler xử lý 403).
 *
 * Trigger trong các trường hợp:
 * - Không gửi header Authorization.
 * - JwtAuthenticationFilter chạy nhưng validateAndParse(token) thất bại
 *   (hết hạn, sai chữ ký, sai issuer, không phải access token...) nên
 *   SecurityContext không có Authentication -> request đi tiếp đến endpoint
 *   được bảo vệ -> Spring Security tự gọi entry point này.
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static  final Logger log = LoggerFactory.getLogger(JwtAuthenticationEntryPoint.class);

    private final ObjectMapper objectMapper;


    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence (
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {

        // Log chi tiết lý do thất bại để debug, nhưng KHÔNG trả chi tiết này
        // ra client - tránh lộ thông tin giúp kẻ tấn công dò cơ chế xác thực
        // (ví dụ phân biệt "token hết hạn" với "sai chữ ký").
        log.warn("Truy cập bị từ chối - chưa xác thực: {} {} - {}",
                request.getMethod(), request.getRequestURI(), authException.getMessage());

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpServletResponse.SC_UNAUTHORIZED);
        body.put("error", "Unauthorized");
        body.put("message", "Yêu cầu đăng nhập hoặc access token không hợp lệ/đã hết hạn.");
        body.put("path", request.getRequestURI());

        objectMapper.writeValue(response.getWriter(), body);


    }
}
