package com.smartbike.rental.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {
    private String token;
    private String refreshToken;
    private UUID userId;
    private String email;
    private String firstName;
    private String lastName;
    private String role;
    private BigDecimal walletBalance;
}
