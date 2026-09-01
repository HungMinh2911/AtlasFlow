package com.atlasflow.security.handler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.*;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class AuthenticationFailureHandlerImpl implements AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationFailureHandlerImpl.class);

    private final ObjectMapper objectMapper;

    public AuthenticationFailureHandlerImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {

        String errorMessage;
        String errorCode;

        if (exception instanceof BadCredentialsException) {
            errorMessage = "Invalid email or password";
            errorCode = "AUTH_INVALID_CREDENTIALS";
        } else if (exception instanceof LockedException) {
            errorMessage = "Your account has been locked. Please contact support.";
            errorCode = "AUTH_ACCOUNT_LOCKED";
        } else if (exception instanceof DisabledException) {
            errorMessage = "Your account is disabled. Please verify your email or contact support.";
            errorCode = "AUTH_ACCOUNT_DISABLED";
        } else if (exception instanceof AccountExpiredException) {
            errorMessage = "Your account has expired.";
            errorCode = "AUTH_ACCOUNT_EXPIRED";
        } else if (exception instanceof CredentialsExpiredException) {
            errorMessage = "Your password has expired. Please reset your password.";
            errorCode = "AUTH_PASSWORD_EXPIRED";
        } else {
            errorMessage = "Authentication failed";
            errorCode = "AUTH_FAILED";
        }

        log.warn("Login failed from IP: [{}], URI: [{}], ErrorCode: [{}]",
                getClientIp(request),
                request.getRequestURI(),
                errorCode);

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> errorDetails = new LinkedHashMap<>();
        errorDetails.put("timestamp", Instant.now().toString());
        errorDetails.put("status", HttpStatus.UNAUTHORIZED.value());
        errorDetails.put("error", "Unauthorized");
        errorDetails.put("code", errorCode);
        errorDetails.put("message", errorMessage);
        errorDetails.put("path", request.getRequestURI());

        objectMapper.writeValue(response.getOutputStream(),errorDetails);

    }

    /**
     * Helper lấy IP thực tế của client kể cả khi đứng sau Proxy / Load Balancer (Nginx, Cloudflare)
     */

    private String getClientIp (HttpServletRequest request){
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if(xForwardedFor != null && !xForwardedFor.isBlank()){
            return xForwardedFor.split(",")[0].trim();
        }
        return  request.getRemoteAddr();
    }


}
