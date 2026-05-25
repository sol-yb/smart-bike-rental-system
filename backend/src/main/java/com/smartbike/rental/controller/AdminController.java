package com.smartbike.rental.controller;

import com.smartbike.rental.dto.AnalyticsDto;
import com.smartbike.rental.dto.BikeDto;
import com.smartbike.rental.model.Notification;
import com.smartbike.rental.model.Payment;
import com.smartbike.rental.model.User;
import com.smartbike.rental.repository.BikeRepository;
import com.smartbike.rental.repository.NotificationRepository;
import com.smartbike.rental.repository.PaymentRepository;
import com.smartbike.rental.repository.UserRepository;
import com.smartbike.rental.service.BikeService;
import com.smartbike.rental.service.RideService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private BikeService bikeService;

    @Autowired
    private RideService rideService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BikeRepository bikeRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @GetMapping("/analytics")
    public ResponseEntity<AnalyticsDto> getAnalytics() {
        long totalBikes = bikeRepository.count();
        long activeRides = rideService.getActiveRides().size();
        long totalUsers = userRepository.count();
        
        BigDecimal totalRevenue = paymentRepository.findAll().stream()
                .filter(p -> "SUCCESS".equalsIgnoreCase(p.getStatus()) && p.getRide() != null)
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long theftAlertsCount = notificationRepository.findAll().stream()
                .filter(n -> "THEFT_ALERT".equalsIgnoreCase(n.getType()))
                .count();

        AnalyticsDto dto = AnalyticsDto.builder()
                .totalBikes(totalBikes)
                .activeRides(activeRides)
                .totalUsers(totalUsers)
                .totalRevenue(totalRevenue)
                .theftAlertsCount(theftAlertsCount)
                .build();

        return ResponseEntity.ok(dto);
    }

    @PostMapping("/bikes")
    public ResponseEntity<BikeDto> addBike(@Valid @RequestBody BikeDto bikeDto) {
        return ResponseEntity.ok(bikeService.addBike(bikeDto));
    }

    @PutMapping("/bikes/{id}")
    public ResponseEntity<BikeDto> updateBike(@PathVariable UUID id, @Valid @RequestBody BikeDto bikeDto) {
        return ResponseEntity.ok(bikeService.updateBike(id, bikeDto));
    }

    @DeleteMapping("/bikes/{id}")
    public ResponseEntity<Void> deleteBike(@PathVariable UUID id) {
        bikeService.deleteBike(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users")
    public ResponseEntity<List<User>> getUsers() {
        return ResponseEntity.ok(userRepository.findAll());
    }

    @GetMapping("/theft-alerts")
    public ResponseEntity<List<Notification>> getTheftAlerts() {
        List<Notification> alerts = notificationRepository.findAll().stream()
                .filter(n -> "THEFT_ALERT".equalsIgnoreCase(n.getType()))
                .toList();
        return ResponseEntity.ok(alerts);
    }
}
