package com.smartbike.rental.service;

import com.smartbike.rental.dto.AuthRequest;
import com.smartbike.rental.dto.AuthResponse;
import com.smartbike.rental.dto.RegisterRequest;
import com.smartbike.rental.exception.BadRequestException;
import com.smartbike.rental.exception.ResourceNotFoundException;
import com.smartbike.rental.model.RefreshToken;
import com.smartbike.rental.model.Role;
import com.smartbike.rental.model.User;
import com.smartbike.rental.repository.UserRepository;
import com.smartbike.rental.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private AuditLogService auditLogService;

    // Security Lockout Maps
    private final Map<String, Integer> failedLoginAttempts = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> lockedAccounts = new ConcurrentHashMap<>();

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCKOUT_DURATION_MINUTES = 15;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            auditLogService.log(null, request.getEmail(), "REGISTER_FAILED", null, "Attempted to register already existing email");
            throw new BadRequestException("Email is already registered");
        }

        Role role = userRepository.count() == 0 ? Role.ROLE_ADMIN : Role.ROLE_USER;

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .role(role)
                .walletBalance(BigDecimal.valueOf(100.0)) // Give 100 bonus balance for testing!
                .verified(true)
                .build();

        user = userRepository.save(user);

        String token = jwtTokenProvider.generateToken(user.getEmail(), user.getRole().name(), user.getId());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getId());

        auditLogService.log(user.getId(), user.getEmail(), "USER_REGISTER", null, "New user registered with role " + role.name());

        return AuthResponse.builder()
                .token(token)
                .refreshToken(refreshToken.getToken())
                .userId(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole().name())
                .walletBalance(user.getWalletBalance())
                .build();
    }

    public AuthResponse login(AuthRequest request) {
        String email = request.getEmail();

        // 1. Check account lockout status
        if (isAccountLocked(email)) {
            auditLogService.log(null, email, "LOGIN_BLOCKED", null, "Access blocked due to active account lockout");
            throw new BadRequestException("Account is temporarily locked due to multiple failed login attempts. Please try again after " + LOCKOUT_DURATION_MINUTES + " minutes.");
        }

        // 2. Fetch user
        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            handleFailedLoginAttempt(email);
            throw new BadRequestException("Invalid email or password");
        }

        // 3. Clear failed login tracking on success
        failedLoginAttempts.remove(email);
        lockedAccounts.remove(email);

        String token = jwtTokenProvider.generateToken(user.getEmail(), user.getRole().name(), user.getId());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getId());

        auditLogService.log(user.getId(), user.getEmail(), "LOGIN_SUCCESS", null, "User successfully authenticated");

        return AuthResponse.builder()
                .token(token)
                .refreshToken(refreshToken.getToken())
                .userId(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole().name())
                .walletBalance(user.getWalletBalance())
                .build();
    }

    private boolean isAccountLocked(String email) {
        LocalDateTime lockoutExpiration = lockedAccounts.get(email);
        if (lockoutExpiration == null) {
            return false;
        }
        if (lockoutExpiration.isBefore(LocalDateTime.now())) {
            lockedAccounts.remove(email);
            failedLoginAttempts.remove(email);
            return false;
        }
        return true;
    }

    private void handleFailedLoginAttempt(String email) {
        int attempts = failedLoginAttempts.getOrDefault(email, 0) + 1;
        failedLoginAttempts.put(email, attempts);

        if (attempts >= MAX_FAILED_ATTEMPTS) {
            lockedAccounts.put(email, LocalDateTime.now().plusMinutes(LOCKOUT_DURATION_MINUTES));
            auditLogService.log(null, email, "SUSPICIOUS_LOGIN", null, 
                    String.format("Suspicious login activity: %d consecutive failed attempts. Account locked.", attempts));
        } else {
            auditLogService.log(null, email, "LOGIN_FAILURE", null, 
                    String.format("Invalid login credentials. Failed attempt %d of %d.", attempts, MAX_FAILED_ATTEMPTS));
        }
    }

    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    public User getUserById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
