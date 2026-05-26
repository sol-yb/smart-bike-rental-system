package com.smartbike.rental.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TokenRefreshRequest {
    @NotBlank(message = "Refresh token cannot be blank")
    private String refreshToken;
}
