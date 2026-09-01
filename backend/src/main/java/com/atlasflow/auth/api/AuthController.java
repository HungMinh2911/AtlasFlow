package com.atlasflow.auth.api;

import com.atlasflow.auth.application.AuthService;
import com.atlasflow.users.domain.User;
import com.atlasflow.ratelimit.RateLimitPolicy;
import com.atlasflow.ratelimit.RateLimited;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Các API xác thực: Đăng ký, Đăng nhập và Xoay vòng Refresh Token (Rotation)")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @Operation(summary = "Đăng ký tài khoản", description = "Kiểm tra email trùng lặp, băm mật khẩu và tạo người dùng mới.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Đăng ký thành công"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu gửi lên không hợp lệ"),
            @ApiResponse(responseCode = "409", description = "Email đã tồn tại")
    })
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        User user = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "User registered successfully",
                "userId", user.id(),
                "email", user.email()
        ));
    }

    @PostMapping("/login")
    @RateLimited(RateLimitPolicy.LOGIN)
    @Operation(summary = "Đăng nhập hệ thống", description = "Xác thực email và mật khẩu, trả về Access Token (JWT) và Refresh Token.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đăng nhập thành công",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sai email hoặc mật khẩu / Tài khoản bị khóa")
    })
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            jakarta.servlet.http.HttpServletRequest servletRequest
    ) {
        AuthResponse response = authService.login(request, servletRequest.getHeader("X-Request-Id"));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Làm mới Access Token (Refresh Token Rotation)",
            description = "Xác thực Refresh Token, thu hồi token cũ và cấp cặp Access Token + Refresh Token mới. Tự động phát hiện Token Replay Attack.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Làm mới token thành công",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "401", description = "Refresh Token không hợp lệ hoặc đã hết hạn"),
            @ApiResponse(responseCode = "403", description = "Phát hiện Token Replay: Toàn bộ phiên đăng nhập của tài khoản bị hủy")
    })
    public ResponseEntity<AuthResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest request,
            jakarta.servlet.http.HttpServletRequest servletRequest
    ) {
        AuthResponse response = authService.refreshToken(
                request.getRefreshToken(),
                servletRequest.getHeader("X-Request-Id")
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/change-password")
    @Operation(summary = "Đổi mật khẩu người dùng", description = "Xác thực mật khẩu hiện tại, cập nhật mật khẩu mới và ghi nhận Audit Log.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đổi mật khẩu thành công"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu gửi lên không hợp lệ"),
            @ApiResponse(responseCode = "401", description = "Mật khẩu hiện tại không đúng / Chưa đăng nhập")
    })
    public ResponseEntity<Map<String, String>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            jakarta.servlet.http.HttpServletRequest servletRequest
    ) {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof com.atlasflow.security.user.CustomUserDetails userDetails)) {
            throw new org.springframework.security.access.AccessDeniedException("Yêu cầu đăng nhập để đổi mật khẩu");
        }
        String requestId = servletRequest.getHeader("X-Request-Id");
        authService.changePassword(userDetails.getId(), request.getCurrentPassword(), request.getNewPassword(), requestId);
        return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
    }
}
