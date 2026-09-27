package com.atlasflow.security;

import com.atlasflow.audit.application.port.AuditLogRepository;
import com.atlasflow.audit.domain.AuditAction;
import com.atlasflow.audit.domain.AuditLog;
import com.atlasflow.auth.api.ChangePasswordRequest;
import com.atlasflow.auth.api.LoginRequest;
import com.atlasflow.auth.api.RefreshTokenRequest;
import com.atlasflow.auth.api.RegisterRequest;
import com.atlasflow.integration.AbstractBaseIntegrationTest;
import com.atlasflow.membership.api.UpdateRoleRequest;
import com.atlasflow.users.application.port.UserRepository;
import com.atlasflow.users.domain.User;
import com.atlasflow.users.domain.UserStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class SecurityAcceptanceIntegrationTest extends AbstractBaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.issuer:atlasflow_backend}")
    private String jwtIssuer;

    @BeforeEach
    void clearRedisRateLimitState() {
        // Keep security acceptance tests independent from login attempts made by other tests.
        stringRedisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    // ========================================================================
    // DoD 1: Happy Path
    // Đăng ký -> Đăng nhập -> Nhận Access/Refresh Token -> Gọi API với Bearer Token (200 OK)
    // ========================================================================
    @Test
    @DisplayName("DoD 1 [Happy Path]: Đăng ký -> Đăng nhập -> Gọi protected API thành công với Bearer token")
    void dod1_HappyPath_Register_Login_CallProtectedApi() throws Exception {
        String email = "happy_" + UUID.randomUUID() + "@atlasflow.io";
        String password = "StrongPassword123!";

        // 1. Đăng ký
        RegisterRequest registerReq = new RegisterRequest(email, password);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.userId").isNotEmpty());

        // 2. Đăng nhập
        LoginRequest loginReq = new LoginRequest(email, password);
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn();

        JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String accessToken = loginJson.get("accessToken").asText();

        // 3. Gọi protected endpoint bằng Bearer token
        mockMvc.perform(get("/api/v1/projects")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    // ========================================================================
    // DoD 2: Invalid Token
    // Token sai chữ ký, token hết hạn, không có token -> Trả về đúng 401 Unauthorized
    // ========================================================================
    @Test
    @DisplayName("DoD 2 [Invalid Token]: Token sai chữ ký hoặc hết hạn phải trả về 401 Unauthorized")
    void dod2_InvalidToken_RejectedWith401() throws Exception {
        // 2.1 Không có token
        mockMvc.perform(get("/api/v1/projects"))
                .andExpect(status().isUnauthorized());

        // 2.2 Token sai chữ ký (chữ ký bị sửa đổi)
        String fakeToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
                "eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkZha2UiLCJpYXQiOjE1MTYyMzkwMjJ9." +
                "TamperedSignatureInvalidKey12345678901234567890";
        mockMvc.perform(get("/api/v1/projects")
                        .header("Authorization", "Bearer " + fakeToken))
                .andExpect(status().isUnauthorized());

        // 2.3 Token đã hết hạn
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
        String expiredToken = Jwts.builder()
                .header().type("JWT").and()
                .id(UUID.randomUUID().toString())
                .issuer(jwtIssuer)
                .subject(UUID.randomUUID().toString())
                .claim("email", "expired@atlasflow.io")
                .claim("roles", List.of())
                .claim("token_type", "ACCESS")
                .issuedAt(Date.from(Instant.now().minus(2, ChronoUnit.HOURS)))
                .expiration(Date.from(Instant.now().minus(1, ChronoUnit.HOURS)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        mockMvc.perform(get("/api/v1/projects")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }

    // ========================================================================
    // DoD 3: Token Rotation
    // Dùng Refresh Token đổi token mới -> Refresh Token cũ bị vô hiệu hóa
    // ========================================================================
    @Test
    @DisplayName("DoD 3 [Token Rotation]: Đổi token mới thành công và vô hiệu hóa Refresh Token cũ")
    void dod3_TokenRotation_OldTokenRevoked_NewTokenIssued() throws Exception {
        String email = "rotation_" + UUID.randomUUID() + "@atlasflow.io";
        String password = "StrongPassword123!";

        // Đăng ký và đăng nhập
        registerAndLogin(email, password);
        LoginRequest loginReq = new LoginRequest(email, password);
        MvcResult loginRes = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andReturn();
        String oldRefreshToken = objectMapper.readTree(loginRes.getResponse().getContentAsString()).get("refreshToken").asText();

        // Đổi token qua /api/auth/refresh
        RefreshTokenRequest refreshReq = new RefreshTokenRequest(oldRefreshToken);
        MvcResult refreshRes = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        JsonNode refreshJson = objectMapper.readTree(refreshRes.getResponse().getContentAsString());
        String newAccessToken = refreshJson.get("accessToken").asText();
        String newRefreshToken = refreshJson.get("refreshToken").asText();

        assertThat(newRefreshToken).isNotEqualTo(oldRefreshToken);

        // Kiểm tra trong DB: token cũ đã bị revoked = true
        Boolean oldRevoked = jdbcClient.sql("SELECT revoked FROM refresh_tokens WHERE replaced_by_token IS NOT NULL ORDER BY created_at DESC LIMIT 1")
                .query(Boolean.class)
                .single();
        assertThat(oldRevoked).isTrue();

        // Token mới dùng được để gọi protected API
        mockMvc.perform(get("/api/v1/projects")
                        .header("Authorization", "Bearer " + newAccessToken))
                .andExpect(status().isOk());
    }

    // ========================================================================
    // DoD 4: Token Replay Attack
    // Dùng lại Refresh Token cũ -> Bị từ chối (403) và kích hoạt thu hồi toàn bộ token family
    // ========================================================================
    @Test
    @DisplayName("DoD 4 [Token Replay Attack]: Gửi lại token đã bị revoke -> 403 và thu hồi toàn bộ chuỗi token")
    void dod4_TokenReplayAttack_RejectedWith403_RevokesAllTokens() throws Exception {
        String email = "replay_" + UUID.randomUUID() + "@atlasflow.io";
        String password = "StrongPassword123!";

        registerAndLogin(email, password);
        LoginRequest loginReq = new LoginRequest(email, password);
        MvcResult loginRes = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andReturn();
        String oldRefreshToken = objectMapper.readTree(loginRes.getResponse().getContentAsString()).get("refreshToken").asText();

        // 1. Xoay vòng hợp lệ lần 1
        RefreshTokenRequest refreshReq = new RefreshTokenRequest(oldRefreshToken);
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq)))
                .andExpect(status().isOk());

        // 2. Kẻ tấn công cố tình gửi lại oldRefreshToken (Replay Attack)
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TOKEN_REPLAY_DETECTED"));

        // 3. Xác nhận TOÀN BỘ Refresh Token của user bị revoked = true
        User user = userRepository.findByEmail(email).orElseThrow();
        List<Boolean> allRevoked = jdbcClient.sql("SELECT revoked FROM refresh_tokens WHERE user_id = :userId")
                .param("userId", user.id())
                .query(Boolean.class)
                .list();

        assertThat(allRevoked).isNotEmpty();
        assertThat(allRevoked).allMatch(revoked -> revoked);

        // 4. Xác nhận Audit Log đã ghi nhận TOKEN_REPLAY_ATTACK
        List<AuditLog> auditLogs = auditLogRepository.findByAction(AuditAction.TOKEN_REPLAY_ATTACK);
        assertThat(auditLogs).anyMatch(log -> user.id().equals(log.actorId()));
    }

    // ========================================================================
    // DoD 5: Cross-Tenant Test (Negative Test)
    // User thuộc Org A dùng token hợp lệ cố tình truy cập resource của Org B -> Phải bị chặn 403 Forbidden
    // ========================================================================
    @Test
    @DisplayName("DoD 5 [Cross-Tenant Test]: User thuộc Org A truy cập Org B -> 403 Forbidden & ghi nhận Audit Log")
    void dod5_CrossTenantAccess_Forbidden_LoggedToAudit() throws Exception {
        // 1. Tạo 2 tổ chức Org A và Org B
        UUID orgAId = UUID.randomUUID();
        UUID orgBId = UUID.randomUUID();
        createOrganization(orgAId, "Org Alpha");
        createOrganization(orgBId, "Org Beta");

        UUID roleAId = UUID.randomUUID();
        UUID roleBId = UUID.randomUUID();
        createRole(roleAId, orgAId, "MEMBER");
        createRole(roleBId, orgBId, "MEMBER");

        // 2. Đăng ký User A và gán vào Org A (không thuộc Org B)
        String userAEmail = "usera_" + UUID.randomUUID() + "@atlasflow.io";
        String userAPass = "Password123!";
        User userA = registerAndLogin(userAEmail, userAPass);
        addMembership(userA.id(), orgAId, roleAId);

        // Lấy access token của User A
        String tokenA = getAccessToken(userAEmail, userAPass);

        // 3. User A truy cập Org A của mình -> 200 OK
        mockMvc.perform(get("/api/v1/projects/organizations/" + orgAId)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());

        // 4. User A cố tình truy cập Org B -> 403 Forbidden
        mockMvc.perform(get("/api/v1/projects/organizations/" + orgBId)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        // 5. Kiểm tra Audit Log đã ghi nhận hành vi UNAUTHORIZED_ACCESS đối với Org B
        List<AuditLog> unauthorizedLogs = auditLogRepository.findByAction(AuditAction.UNAUTHORIZED_ACCESS);
        assertThat(unauthorizedLogs).anyMatch(log ->
                orgBId.equals(log.organizationId()) && userA.id().equals(log.actorId())
        );
    }

    // ========================================================================
    // Audit Logging Verification: Hành vi nhạy cảm
    // Failed login lockout, password change, membership role change
    // ========================================================================
    @Test
    @DisplayName("Audit Logging: Đăng nhập sai 5 lần khóa tài khoản; Đổi mật khẩu; Đổi quyền membership")
    void auditLogging_SensitiveActions_RecordedProperly() throws Exception {
        // A. Đăng nhập sai 5 lần -> khóa tài khoản LOCKED
        String email = "lockout_" + UUID.randomUUID() + "@atlasflow.io";
        String correctPass = "CorrectPass123!";
        registerAndLogin(email, correctPass);

        LoginRequest wrongLoginReq = new LoginRequest(email, "WrongPassword!");
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(wrongLoginReq)))
                    .andExpect(status().isUnauthorized());
        }

        // Kiểm tra User đã bị chuyển sang LOCKED
        User user = userRepository.findByEmail(email).orElseThrow();
        assertThat(user.status()).isEqualTo(UserStatus.LOCKED);

        // Lần thứ 6 kể cả mật khẩu đúng cũng bị 401 vì tài khoản bị khóa
        LoginRequest correctLoginReq = new LoginRequest(email, correctPass);
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "203.0.113.10")
                        .content(objectMapper.writeValueAsString(correctLoginReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_ACCOUNT_LOCKED"));

        // Kiểm tra Audit Log có MAX_LOGIN_ATTEMPTS_EXCEEDED
        List<AuditLog> lockLogs = auditLogRepository.findByAction(AuditAction.MAX_LOGIN_ATTEMPTS_EXCEEDED);
        assertThat(lockLogs).anyMatch(log -> user.id().equals(log.actorId()));

        // B. Đổi mật khẩu
        String passEmail = "passchange_" + UUID.randomUUID() + "@atlasflow.io";
        String oldPass = "OldPassword123!";
        String newPass = "NewPassword123!";
        User passUser = registerAndLogin(passEmail, oldPass);
        String passToken = getAccessToken(passEmail, oldPass);

        ChangePasswordRequest changeReq = new ChangePasswordRequest(oldPass, newPass);
        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + passToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changeReq)))
                .andExpect(status().isOk());

        // Kiểm tra Audit Log có PASSWORD_CHANGED
        List<AuditLog> passLogs = auditLogRepository.findByAction(AuditAction.PASSWORD_CHANGED);
        assertThat(passLogs).anyMatch(log -> passUser.id().equals(log.actorId()));

        // Đăng nhập bằng mật khẩu mới thành công
        LoginRequest newLoginReq = new LoginRequest(passEmail, newPass);
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newLoginReq)))
                .andExpect(status().isOk());

        // C. Đổi quyền membership
        UUID orgId = UUID.randomUUID();
        createOrganization(orgId, "Membership Test Org");
        UUID role1 = UUID.randomUUID();
        UUID role2 = UUID.randomUUID();
        createRole(role1, orgId, "VIEWER");
        createRole(role2, orgId, "ADMIN");

        addMembership(passUser.id(), orgId, role1);

        UpdateRoleRequest roleReq = new UpdateRoleRequest(role2);
        mockMvc.perform(put("/api/v1/organizations/" + orgId + "/memberships/" + passUser.id() + "/role")
                        .header("Authorization", "Bearer " + passToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roleReq)))
                .andExpect(status().isOk());

        // Kiểm tra Audit Log có MEMBERSHIP_ROLE_CHANGED
        List<AuditLog> roleLogs = auditLogRepository.findByAction(AuditAction.MEMBERSHIP_ROLE_CHANGED);
        assertThat(roleLogs).anyMatch(log -> orgId.equals(log.organizationId()));
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================
    private User registerAndLogin(String email, String password) throws Exception {
        RegisterRequest registerReq = new RegisterRequest(email, password);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated());
        return userRepository.findByEmail(email).orElseThrow();
    }

    private String getAccessToken(String email, String password) throws Exception {
        LoginRequest loginReq = new LoginRequest(email, password);
        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private void createOrganization(UUID orgId, String name) {
        jdbcClient.sql("INSERT INTO organizations (id, name, created_at) VALUES (:id, :name, NOW())")
                .param("id", orgId)
                .param("name", name)
                .update();
    }

    private void createRole(UUID roleId, UUID orgId, String name) {
        jdbcClient.sql("INSERT INTO roles (id, organization_id, name) VALUES (:id, :orgId, :name)")
                .param("id", roleId)
                .param("orgId", orgId)
                .param("name", name)
                .update();
    }

    private void addMembership(UUID userId, UUID orgId, UUID roleId) {
        jdbcClient.sql("INSERT INTO memberships (user_id, organization_id, role_id, status) VALUES (:userId, :orgId, :roleId, 'ACTIVE')")
                .param("userId", userId)
                .param("orgId", orgId)
                .param("roleId", roleId)
                .update();
    }
}
