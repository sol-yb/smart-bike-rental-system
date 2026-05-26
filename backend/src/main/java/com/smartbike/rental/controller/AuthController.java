package com.smartbike.rental.controller;

import com.smartbike.rental.dto.AuthRequest;
import com.smartbike.rental.dto.AuthResponse;
import com.smartbike.rental.dto.RegisterRequest;
import com.smartbike.rental.dto.TokenRefreshRequest;
import com.smartbike.rental.dto.TokenRefreshResponse;
import com.smartbike.rental.exception.BadRequestException;
import com.smartbike.rental.model.RefreshToken;
import com.smartbike.rental.security.JwtTokenProvider;
import com.smartbike.rental.service.AuthService;
import com.smartbike.rental.service.RefreshTokenService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenRefreshResponse> refresh(@Valid @RequestBody TokenRefreshRequest request) {
        String requestRefreshToken = request.getRefreshToken();

        return refreshTokenService.findByToken(requestRefreshToken)
                .map(refreshTokenService::verifyExpiration)
                .map(RefreshToken::getUser)
                .map(user -> {
                    String token = jwtTokenProvider.generateToken(user.getEmail(), user.getRole().name(), user.getId());
                    // Rotate the refresh token for maximum security
                    RefreshToken newRefreshToken = refreshTokenService.createRefreshToken(user.getId());
                    return ResponseEntity.ok(TokenRefreshResponse.builder()
                            .accessToken(token)
                            .refreshToken(newRefreshToken.getToken())
                            .build());
                })
                .orElseThrow(() -> new BadRequestException("Refresh token is not in database!"));
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logoutUser(@Valid @RequestBody TokenRefreshRequest request) {
        refreshTokenService.findByToken(request.getRefreshToken())
                .ifPresent(token -> refreshTokenService.deleteByUserId(token.getUser().getId()));
        return ResponseEntity.ok("Log out successful!");
    }
}
