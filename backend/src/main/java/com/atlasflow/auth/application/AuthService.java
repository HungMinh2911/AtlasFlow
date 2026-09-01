package com.atlasflow.auth.application;

import com.atlasflow.auth.api.AuthResponse;
import com.atlasflow.auth.api.LoginRequest;
import com.atlasflow.auth.api.RegisterRequest;
import com.atlasflow.auth.domain.RefreshToken;
import com.atlasflow.auth.application.port.RefreshTokenRepository;
import com.atlasflow.security.jwt.JwtTokenProvider;
import com.atlasflow.security.user.CustomUserDetails;
import com.atlasflow.users.application.port.UserRepository;
import com.atlasflow.users.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final com.atlasflow.audit.application.AuditLogService auditLogService;

    @Autowired(required = false)
    private com.atlasflow.security.user.CustomUserDetailsService customUserDetailsService;

    @Value("${jwt.refresh-expiration-days:7}")
    private int refreshExpirationDays;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtTokenProvider jwtTokenProvider,
            RefreshTokenRepository refreshTokenRepository,
            com.atlasflow.audit.application.AuditLogService auditLogService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenRepository = refreshTokenRepository;
        this.auditLogService = auditLogService;
    }

    /**
     * Đăng ký tài khoản người dùng mới
     */
    @Transactional
    public User register(RegisterRequest request) {
        String email = request.getEmail();
        if (email == null || email.isBlank()) {
            email = request.getUsername();
        }

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already exists: " + email);
        }

        String passwordHash = passwordEncoder.encode(request.getPassword());
        User newUser = User.create(UUID.randomUUID(), email, passwordHash);

        User savedUser = userRepository.save(newUser);
        log.info("Đăng ký tài khoản thành công cho email: {}", email);
        return savedUser;
    }

    /**
     * Đăng nhập: Xác thực và cấp cặp Access Token + Refresh Token
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        return login(request, null);
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String requestId) {
        String loginIdentifier = request.getEmail();
        if (loginIdentifier == null || loginIdentifier.isBlank()) {
            loginIdentifier = request.getUsername();
        }

        // 1. Xác thực email và password thông qua AuthenticationManager
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginIdentifier, request.getPassword())
            );
        } catch (org.springframework.security.core.AuthenticationException ex) {
            auditLogService.logFailedLogin(loginIdentifier, requestId);
            throw ex;
        }

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        // 2. Tạo Access Token (JWT Stateless)
        String accessToken = jwtTokenProvider.generateAccessToken(authentication);

        // 3. Tạo Refresh Token ngẫu nhiên an toàn (256-bit cryptographically secure)
        String rawRefreshToken = generateSecureRandomToken();
        String tokenHash = hash(rawRefreshToken);

        // 4. Khởi tạo một chuỗi token family mới và lưu vào DB
        UUID familyId = UUID.randomUUID();
        RefreshToken refreshToken = new RefreshToken(
                UUID.randomUUID(),
                tokenHash,
                userDetails.getId(),
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(refreshExpirationDays),
                false,
                null,
                familyId
        );
        refreshTokenRepository.save(refreshToken);

        log.info("Đăng nhập thành công user: [{}], family_id: [{}]", userDetails.getUsername(), familyId);
        return new AuthResponse(accessToken, rawRefreshToken);
    }

    /**
     * Refresh Token Rotation & Phát hiện Token Replay
     */
    @Transactional(noRollbackFor = SecurityException.class)
    public AuthResponse refreshToken(String rawRefreshToken) {
        return refreshToken(rawRefreshToken, null);
    }

    @Transactional(noRollbackFor = SecurityException.class)
    public AuthResponse refreshToken(String rawRefreshToken, String requestId) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new BadCredentialsException("Refresh token is required");
        }

        // 1. Kiểm tra sự tồn tại của Refresh Token trong DB bằng hash
        RefreshToken existingToken = refreshTokenRepository.findByToken(rawRefreshToken)
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        // 2. Phát hiện Token Replay Attack:
        // Nếu một Refresh Token đã bị thu hồi (revoked = true) mà vẫn được gửi lên,
        // đây là dấu hiệu Token đã bị đánh cắp hoặc bị tấn công Replay!
        if (Boolean.TRUE.equals(existingToken.revoked())) {
            log.error("CẢNH BÁO NGUY CẤP (TOKEN REPLAY ATTACK): Token đã bị revoke được tái sử dụng! User ID: {}, Family ID: {}",
                    existingToken.user_id(), existingToken.family_id());

            // Ngay lập tức thu hồi TOÀN BỘ các Refresh Token của User đó (vô hiệu hóa tất cả phiên đăng nhập)
            refreshTokenRepository.revokeAllByUserId(existingToken.user_id());

            // Ghi nhận Audit Log
            auditLogService.logTokenReplay(existingToken.user_id(), requestId);

            throw new SecurityException("Token replay detected. All active sessions for this account have been terminated for security.");
        }

        // 3. Kiểm tra thời hạn hết hạn của Refresh Token
        if (existingToken.expires_at().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.revokeToken(rawRefreshToken);
            throw new BadCredentialsException("Refresh token has expired");
        }

        // 4. Thực hiện Refresh Token Rotation:
        // Cấp 1 cặp Access Token + Refresh Token mới hoàn toàn
        String newRawRefreshToken = generateSecureRandomToken();
        String newTokenHash = hash(newRawRefreshToken);
        UUID newRefreshTokenId = UUID.randomUUID();

        // 4.1. Lưu token mới vào DB trước (để thoả mãn FK fk_refresh_tokens_replaced_by_token)
        RefreshToken newRefreshToken = new RefreshToken(
                newRefreshTokenId,
                newTokenHash,
                existingToken.user_id(),
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(refreshExpirationDays),
                false,
                null,
                existingToken.family_id()
        );
        refreshTokenRepository.save(newRefreshToken);

        // 4.2. Đánh dấu token cũ bị thu hồi và lưu vết trỏ sang token mới
        refreshTokenRepository.revokeById(existingToken.id(), newRefreshTokenId);

        // 4.3. Nạp lại thông tin User và cấp Access Token mới
        User user = userRepository.findById(existingToken.user_id())
                .orElseThrow(() -> new UsernameNotFoundException("User not found with ID: " + existingToken.user_id()));

        CustomUserDetails userDetails = customUserDetailsService == null
                ? new CustomUserDetails(user)
                : (CustomUserDetails) customUserDetailsService.loadUserByUsername(user.email());
        List<String> authorities = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        String newAccessToken = jwtTokenProvider.generateAccessToken(user.id(), user.email(), authorities);

        log.info("Xoay vòng token thành công cho User ID: [{}], Family ID: [{}]", user.id(), existingToken.family_id());
        return new AuthResponse(newAccessToken, newRawRefreshToken);
    }

    private String generateSecureRandomToken() {
        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    public static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Thay đổi mật khẩu người dùng và ghi nhận Audit Log
     */
    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword, String requestId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with ID: " + userId));

        if (!passwordEncoder.matches(currentPassword, user.passwordHash())) {
            throw new BadCredentialsException("Current password does not match");
        }

        String newHash = passwordEncoder.encode(newPassword);
        User updatedUser = new User(user.id(), user.email(), newHash, user.status(), user.createdAt());
        userRepository.save(updatedUser);

        // Ghi nhận Audit Log
        auditLogService.logPasswordChanged(userId, requestId);
        log.info("Mật khẩu của user [{}] đã được thay đổi thành công", user.email());
    }
}
