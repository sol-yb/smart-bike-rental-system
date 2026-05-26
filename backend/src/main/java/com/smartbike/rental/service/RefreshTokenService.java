package com.smartbike.rental.service;

import com.smartbike.rental.exception.BadRequestException;
import com.smartbike.rental.exception.ResourceNotFoundException;
import com.smartbike.rental.model.RefreshToken;
import com.smartbike.rental.model.User;
import com.smartbike.rental.repository.RefreshTokenRepository;
import com.smartbike.rental.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenService {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepository.findByToken(token);
    }

    @Transactional
    public RefreshToken createRefreshToken(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Delete existing token if present to enforce one session refresh token
        refreshTokenRepository.findByUser(user).ifPresent(token -> refreshTokenRepository.delete(token));

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString() + "-" + UUID.randomUUID().toString())
                .expiryDate(LocalDateTime.now().plusDays(7)) // Valid for 7 days
                .build();

        return refreshTokenRepository.save(refreshToken);
    }

    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.getExpiryDate().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(token);
            throw new BadRequestException("Refresh token was expired. Please make a new signin request");
        }
        return token;
    }

    @Transactional
    public int deleteByUserId(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return refreshTokenRepository.deleteByUser(user);
    }
}
