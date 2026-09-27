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
import com.atlasflow.users.domain.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private com.atlasflow.audit.application.AuditLogService auditLogService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                passwordEncoder,
                authenticationManager,
                jwtTokenProvider,
                refreshTokenRepository,
                auditLogService
        );
    }

    @Test
    @DisplayName("register: thành công khi email chưa tồn tại")
    void register_Success() {
        RegisterRequest request = new RegisterRequest("test@atlasflow.io", "Password123!");
        when(userRepository.existsByEmail("test@atlasflow.io")).thenReturn(false);
        when(passwordEncoder.encode("Password123!")).thenReturn("hashed_password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User user = authService.register(request);

        assertNotNull(user);
        assertEquals("test@atlasflow.io", user.email());
        assertEquals("hashed_password", user.passwordHash());
        assertEquals(UserStatus.ACTIVE, user.status());
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("register: ném ngoại lệ khi email đã tồn tại")
    void register_DuplicateEmail_ThrowsException() {
        RegisterRequest request = new RegisterRequest("duplicate@atlasflow.io", "Password123!");
        when(userRepository.existsByEmail("duplicate@atlasflow.io")).thenReturn(true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                authService.register(request)
        );

        assertTrue(exception.getMessage().contains("Email already exists"));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("login: xác thực thành công và trả về cặp token")
    void login_Success() {
        LoginRequest request = new LoginRequest("test@atlasflow.io", "Password123!");
        UUID userId = UUID.randomUUID();
        User user = new User(userId, "test@atlasflow.io", "hashed_pwd", UserStatus.ACTIVE, LocalDateTime.now());
        CustomUserDetails userDetails = new CustomUserDetails(user);

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(userDetails);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);
        when(jwtTokenProvider.generateAccessToken(auth)).thenReturn("mock_access_token");

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("mock_access_token", response.getAccessToken());
        assertNotNull(response.getRefreshToken());
        assertEquals("Bearer", response.getTokenType());

        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(tokenCaptor.capture());
        RefreshToken savedToken = tokenCaptor.getValue();
        assertEquals(userId, savedToken.user_id());
        assertFalse(savedToken.revoked());
        assertNotNull(savedToken.family_id());
    }

    @Test
    @DisplayName("refreshToken: xoay vòng token thành công (Rotation)")
    void refreshToken_Success() {
        String rawToken = "valid_refresh_token";
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();

        RefreshToken existingToken = new RefreshToken(
                tokenId,
                AuthService.hash(rawToken),
                userId,
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(7),
                false,
                null,
                familyId
        );

        User user = new User(userId, "test@atlasflow.io", "hashed_pwd", UserStatus.ACTIVE, LocalDateTime.now());

        when(refreshTokenRepository.findByToken(rawToken)).thenReturn(Optional.of(existingToken));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(jwtTokenProvider.generateAccessToken(eq(userId), eq("test@atlasflow.io"), any())).thenReturn("new_access_token");

        AuthResponse response = authService.refreshToken(rawToken);

        assertNotNull(response);
        assertEquals("new_access_token", response.getAccessToken());
        assertNotEquals(rawToken, response.getRefreshToken());

        // Token cũ phải bị thu hồi và trỏ replaced_by_token đến token mới
        verify(refreshTokenRepository).revokeById(eq(tokenId), any(UUID.class));

        // Token mới phải được lưu với cùng family_id
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken newToken = captor.getValue();
        assertEquals(familyId, newToken.family_id());
        assertFalse(newToken.revoked());
    }

    @Test
    @DisplayName("refreshToken: phát hiện Token Replay Attack khi token đã bị thu hồi trước đó")
    void refreshToken_ReplayAttack_RevokesAllTokensAndThrowsSecurityException() {
        String replayedToken = "replayed_stolen_token";
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();

        // Token này đã từng bị revoked = true
        RefreshToken revokedToken = new RefreshToken(
                UUID.randomUUID(),
                AuthService.hash(replayedToken),
                userId,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(6),
                true, // Đã bị thu hồi!
                UUID.randomUUID(),
                familyId
        );

        when(refreshTokenRepository.findByToken(replayedToken)).thenReturn(Optional.of(revokedToken));

        SecurityException exception = assertThrows(SecurityException.class, () ->
                authService.refreshToken(replayedToken)
        );

        assertTrue(exception.getMessage().contains("Token replay detected"));
        // Cần thu hồi toàn bộ token của User đó ngay lập tức
        verify(refreshTokenRepository).revokeAllByUserId(userId);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("refreshToken: ném lỗi khi Refresh Token đã hết hạn")
    void refreshToken_ExpiredToken_ThrowsBadCredentialsException() {
        String expiredToken = "expired_token";
        UUID userId = UUID.randomUUID();

        RefreshToken token = new RefreshToken(
                UUID.randomUUID(),
                AuthService.hash(expiredToken),
                userId,
                LocalDateTime.now().minusDays(10),
                LocalDateTime.now().minusDays(1), // Hết hạn!
                false,
                null,
                UUID.randomUUID()
        );

        when(refreshTokenRepository.findByToken(expiredToken)).thenReturn(Optional.of(token));

        BadCredentialsException exception = assertThrows(BadCredentialsException.class, () ->
                authService.refreshToken(expiredToken)
        );

        assertTrue(exception.getMessage().contains("expired"));
        verify(refreshTokenRepository).revokeToken(expiredToken);
        verify(refreshTokenRepository, never()).save(any());
    }
}
