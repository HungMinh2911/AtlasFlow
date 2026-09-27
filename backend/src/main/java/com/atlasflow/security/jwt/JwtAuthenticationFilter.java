package com.atlasflow.security.jwt;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private  static final org.slf4j.Logger log =  LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtTokenProvider jwtTokenProvider;


    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException , IOException{

        String jwt = resloveToken(request);

        if (StringUtils.hasText(jwt)){
            try {

                Optional<Claims> claims = jwtTokenProvider.validateAndParse(jwt);
                if (claims.isPresent()){
                    Authentication authentication = jwtTokenProvider.getAuthentication(claims.get());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }

            } catch (Exception ex) {
                log.warn("Không thể dựng Authentication từ JWT: {}", ex.getMessage());
                SecurityContextHolder.clearContext();;
            }
        }

        filterChain.doFilter(request,response);

    }


    private String resloveToken (HttpServletRequest request){

        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
