package com.atlasflow.auth.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Yêu cầu làm mới Access Token bằng Refresh Token")
public class RefreshTokenRequest {

    @Schema(description = "Refresh Token hợp lệ đã được cấp lúc đăng nhập", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    @NotBlank(message = "Refresh token cannot be blank")
    private String refreshToken;

    public RefreshTokenRequest() {
    }

    public RefreshTokenRequest(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
