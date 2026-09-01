package com.atlasflow.security.jwt;

import com.atlasflow.security.user.CustomUserDetails;
import com.atlasflow.users.domain.User;
import com.atlasflow.users.domain.UserStatus;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private static final  String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLES = "roles";

    private static final String CLAIM_TOKEN_TYPE = "token_type";
    private static final String TOKEN_TYPE_ACCESS = "ACCESS";

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private long jwtExpirationInMs;

    @Value("${jwt.issuer:atlasflow_backend}")
    private String jwtIssuer;

    @Value("${jwt.audience:atlasflow-api}")
    private String jwtAudience;

    private SecretKey singingKey;

    @PostConstruct
    public void init() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        this.singingKey = Keys.hmacShaKeyFor(keyBytes);
        log.info("JwtTokenProvider đã khởi tạo thành công với thuật toán HS256.");
    }

    public String generateAccessToken(Authentication authentication) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        List<String> authorities = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        return buildToken(userDetails.getId(), userDetails.getUsername(), authorities);
    }


    public String generateAccessToken(UUID userid, String email, Collection<String> authorities)
    {
        return buildToken(userid, email, authorities);
    }



    private String buildToken(UUID userId, String email, Collection<String> authorities) {

        Instant now  = Instant.now();
        Instant expiryInstant  = now.plus(jwtExpirationInMs
        , ChronoUnit.MILLIS);

        return Jwts.builder()
                .header()
                .type("JWT")
                .and()
                .id(UUID.randomUUID().toString())
                .issuer(jwtIssuer)
                .audience().add(jwtAudience).and()
                .subject(userId.toString())
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_ROLES, authorities != null ? authorities : Collections.emptyList())
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiryInstant))
                .signWith(singingKey, Jwts.SIG.HS256)
                .compact();

    }


    public Optional<Claims> validateAndParse (String token) {

        if (token == null || token.isEmpty()) {
            log.warn("JWT token is null or empty.");
            return Optional.empty();
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(singingKey)
                    .requireIssuer(jwtIssuer)
                    .requireAudience(jwtAudience)
                    .build()
                    .parseSignedClaims(token)  // cho nay  jjwt se thuc hien verify signature va validate exp, iat, nbf
                    .getPayload();

            String tokenType = claims. get(CLAIM_TOKEN_TYPE, String.class);
            if (!TOKEN_TYPE_ACCESS.equals(tokenType)) {
                log.warn("Invalid token type: {}", tokenType);
                return Optional.empty();
            }

            return Optional.of(claims);
        } catch (SecurityException | SignatureException ex) {
            log.warn("JWT không hợp lệ - Sai chữ ký bảo mật: {}", ex.getMessage());
        } catch (MalformedJwtException ex) {
            log.warn("JWT không hợp lệ - Sai định dạng cấu trúc token: {}", ex.getMessage());
        } catch (ExpiredJwtException ex) {
            log.warn("JWT đã hết hạn sử dụng (Expired): {}", ex.getMessage());
        } catch (UnsupportedJwtException ex) {
            log.warn("JWT không được hỗ trợ bởi hệ thống: {}", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            log.warn("JWT claims rỗng hoặc không hợp lệ: {}", ex.getMessage());
        } catch (JwtException ex) {
            // Bắt các lỗi claim còn lại, ví dụ issuer không khớp (requireIssuer)
            log.warn("JWT không hợp lệ - claim không thoả điều kiện bắt buộc: {}", ex.getMessage());
        }

        return Optional.empty();

    }

    public boolean validateToken(String token) {
        return validateAndParse(token).isPresent();
    }

    public UUID getUserIdFromClaims(Claims claims) {
        return UUID.fromString(claims.getSubject());
    }

    public String getEmailFromClaims(Claims claims) {
        return claims.get(CLAIM_EMAIL, String.class);
    }

    public Date getExpirationFromClaims(Claims claims) {
        return claims.getExpiration();
    }

    public Authentication getAuthentication(Claims claims){

        UUID userId = getUserIdFromClaims(claims);
        String email = getEmailFromClaims(claims);

        @SuppressWarnings("unchecked")
        List<String> roles = claims.get(CLAIM_ROLES, List.class);

        List<GrantedAuthority> authorities =  roles == null ? Collections.emptyList() : roles.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());

        User user = new User(userId, email, "", UserStatus.ACTIVE, null);
        CustomUserDetails principal = new CustomUserDetails(user, authorities);



        return new UsernamePasswordAuthenticationToken(principal, null, authorities);

    }

    public Authentication getAuthentication(String token) {
        Claims claims = validateAndParse(token)
                .orElseThrow(() -> new JwtException("Token không hợp lệ hoặc không phải access token"));
        return getAuthentication(claims);
    }







}
