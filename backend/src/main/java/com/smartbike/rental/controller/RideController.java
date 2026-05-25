package com.smartbike.rental.controller;

import com.smartbike.rental.dto.BookingRequest;
import com.smartbike.rental.dto.RideDto;
import com.smartbike.rental.model.User;
import com.smartbike.rental.service.AuthService;
import com.smartbike.rental.service.RideService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/rides")
public class RideController {

    @Autowired
    private RideService rideService;

    @Autowired
    private AuthService authService;

    private User getAuthenticatedUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return authService.getUserByEmail(email);
    }

    @PostMapping("/start")
    public ResponseEntity<RideDto> startRide(@Valid @RequestBody BookingRequest request) {
        User user = getAuthenticatedUser();
        return ResponseEntity.ok(rideService.startRide(user, request.getQrCode()));
    }

    @PostMapping("/end/{id}")
    public ResponseEntity<RideDto> endRide(
            @PathVariable UUID id,
            @RequestParam double latitude,
            @RequestParam double longitude) {
        return ResponseEntity.ok(rideService.endRide(id, latitude, longitude));
    }

    @GetMapping("/active")
    public ResponseEntity<RideDto> getActiveRide() {
        User user = getAuthenticatedUser();
        return ResponseEntity.ok(rideService.getActiveRideByUser(user.getId()));
    }

    @GetMapping("/history")
    public ResponseEntity<List<RideDto>> getRideHistory() {
        User user = getAuthenticatedUser();
        return ResponseEntity.ok(rideService.getRideHistoryByUser(user.getId()));
    }
}
