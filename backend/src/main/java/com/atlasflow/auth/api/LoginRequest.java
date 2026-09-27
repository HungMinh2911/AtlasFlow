package com.atlasflow.auth.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Thông tin đăng nhập hệ thống")
public class LoginRequest {

    @Schema(description = "Email hoặc Username của tài khoản", example = "admin@atlasflow.io")
    private String username;

    @Schema(description = "Email tài khoản", example = "admin@atlasflow.io")
    private String email;

    @Schema(description = "Mật khẩu tài khoản", example = "Admin@123456")
    @NotBlank(message = "Password cannot be blank")
    private String password;

    public LoginRequest() {
    }

    public LoginRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public String getUsername() {
        if (username != null && !username.isBlank()) {
            return username;
        }
        return email;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        if (email != null && !email.isBlank()) {
            return email;
        }
        return username;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
