package com.atlasflow.auth.api;

import com.atlasflow.auth.application.AuthService;
import com.atlasflow.users.domain.User;
import com.atlasflow.users.domain.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @MockitoBean
    private com.atlasflow.security.jwt.JwtTokenProvider jwtTokenProvider;


    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @Test
    @DisplayName("POST /api/auth/register: trả về 201 khi dữ liệu hợp lệ")
    void register_Returns201() throws Exception {
        RegisterRequest request = new RegisterRequest("john@atlasflow.io", "StrongPass123!");
        User user = new User(UUID.randomUUID(), "john@atlasflow.io", "hashed", UserStatus.ACTIVE, LocalDateTime.now());

        when(authService.register(any(RegisterRequest.class))).thenReturn(user);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("john@atlasflow.io"))
                .andExpect(jsonPath("$.userId").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/auth/login: trả về 200 và cặp token khi đăng nhập thành công")
    void login_Returns200() throws Exception {
        LoginRequest request = new LoginRequest("john@atlasflow.io", "StrongPass123!");
        AuthResponse response = new AuthResponse("mock_access_token", "mock_refresh_token");

        when(authService.login(any(LoginRequest.class), isNull())).thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("mock_access_token"))
                .andExpect(jsonPath("$.refreshToken").value("mock_refresh_token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    @DisplayName("POST /api/auth/refresh: trả về 200 và cặp token mới khi xoay vòng thành công")
    void refresh_Returns200() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest("existing_refresh_token");
        AuthResponse response = new AuthResponse("new_access_token", "new_refresh_token");

        when(authService.refreshToken("existing_refresh_token", null)).thenReturn(response);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new_access_token"))
                .andExpect(jsonPath("$.refreshToken").value("new_refresh_token"));
    }
}
