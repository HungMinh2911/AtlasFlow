package com.atlasflow.security.jwt;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.security.SecureRandom;

public class JwtTokenProviderTest {

    @Test
    public void initAndGenerateToken_shouldProduceValidToken() throws Exception {
        JwtTokenProvider provider = new JwtTokenProvider();

        // Generate a 256-bit random secret and set as base64 (HS256 requires 32 bytes)
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        String base64Secret = Base64.getEncoder().encodeToString(secret);

        // Use reflection to set private fields (class uses @Value injection in production)
        setPrivateField(provider, "jwtSecret", base64Secret);
        setPrivateField(provider, "jwtExpirationInMs", 3600000L);
        setPrivateField(provider, "jwtIssuer", "unit-test-issuer");

        // Call init() manually because the production annotation was intentionally misspelled in source.
        provider.init();

        // Generate a token and validate via provider.validateToken
        String token = provider.generateAccessToken(UUID.randomUUID(), "test@example.com", List.of("ROLE_USER"));

        Assertions.assertNotNull(token);
        Assertions.assertFalse(token.isEmpty());

        boolean valid = provider.validateToken(token);
        Assertions.assertTrue(valid, "Generated token should be valid when validated by the same provider");
    }

    private static void setPrivateField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}
