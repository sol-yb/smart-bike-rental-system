package com.smartbike.rental.service;

import com.smartbike.rental.dto.RideDto;
import com.smartbike.rental.exception.BadRequestException;
import com.smartbike.rental.exception.ResourceNotFoundException;
import com.smartbike.rental.model.Bike;
import com.smartbike.rental.model.BikeState;
import com.smartbike.rental.model.Ride;
import com.smartbike.rental.model.User;
import com.smartbike.rental.repository.BikeRepository;
import com.smartbike.rental.repository.RideRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RideService {

    @Autowired
    private RideRepository rideRepository;

    @Autowired
    private BikeRepository bikeRepository;

    @Autowired
    private MqttService mqttService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private NotificationService notificationService;

    public RideDto getActiveRideByUser(UUID userId) {
        Ride ride = rideRepository.findByUserIdAndActive(userId, true)
                .orElseThrow(() -> new ResourceNotFoundException("No active ride found for user"));
        return mapToDto(ride);
    }

    public List<RideDto> getRideHistoryByUser(UUID userId) {
        return rideRepository.findByUserId(userId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public List<RideDto> getActiveRides() {
        return rideRepository.findByActive(true).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public RideDto startRide(User user, String qrCode) {
        // 1. Check if user already has an active ride
        Optional<Ride> existingRide = rideRepository.findByUserIdAndActive(user.getId(), true);
        if (existingRide.isPresent()) {
            throw new BadRequestException("You already have an active ride.");
        }

        // 2. Check wallet balance
        if (user.getWalletBalance().compareTo(BigDecimal.valueOf(10.0)) < 0) {
            throw new BadRequestException("Minimum balance of 10.0 birr is required to start a ride.");
        }

        // 3. Find and check bike
        Bike bike = bikeRepository.findByQrCode(qrCode)
                .orElseThrow(() -> new ResourceNotFoundException("Bike with QR code " + qrCode + " not found."));

        if (bike.getState() != BikeState.AVAILABLE) {
            throw new BadRequestException("Bike is not available for rental. Current state: " + bike.getState());
        }

        // 4. Update bike state
        bike.setState(BikeState.IN_USE);
        bike.setLocked(false);
        bikeRepository.save(bike);

        // 5. Create ride
        Ride ride = Ride.builder()
                .user(user)
                .bike(bike)
                .startTime(LocalDateTime.now())
                .startLatitude(bike.getLatitude())
                .startLongitude(bike.getLongitude())
                .active(true)
                .build();

        ride = rideRepository.save(ride);

        // 6. Publish MQTT command to unlock physical hardware
        mqttService.publishUnlockCommand(bike.getQrCode());

        notificationService.createNotification(
                user,
                "Ride Started",
                "Your ride on bike " + bike.getQrCode() + " has started. Enjoy your trip!",
                "SYSTEM_ALERT"
        );

        return mapToDto(ride);
    }

    @Transactional
    public RideDto endRide(UUID rideId, double endLat, double endLon) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new ResourceNotFoundException("Ride not found"));

        if (!ride.isActive()) {
            throw new BadRequestException("Ride is already completed.");
        }

        Bike bike = ride.getBike();
        LocalDateTime endTime = LocalDateTime.now();
        ride.setEndTime(endTime);
        ride.setEndLatitude(endLat);
        ride.setEndLongitude(endLon);

        // Calculate distance
        double distance = calculateDistance(
                ride.getStartLatitude(), ride.getStartLongitude(),
                endLat, endLon
        );
        ride.setDistance(distance);

        // Calculate cost
        // Formula: Base 5 birr + 0.5 birr/minute + 2 birr/km
        long minutes = Duration.between(ride.getStartTime(), endTime).toMinutes();
        if (minutes < 1) minutes = 1; // charge at least 1 min
        double costDouble = 5.0 + (minutes * 0.5) + (distance * 2.0);
        BigDecimal cost = BigDecimal.valueOf(costDouble).setScale(2, BigDecimal.ROUND_HALF_UP);
        ride.setCost(cost);
        ride.setActive(false);

        // Update bike state
        bike.setState(BikeState.AVAILABLE);
        bike.setLocked(true);
        bike.setLatitude(endLat);
        bike.setLongitude(endLon);
        bikeRepository.save(bike);

        // Process payment deduction
        paymentService.processRidePayment(ride);

        // Publish MQTT lock command to hardware
        mqttService.publishLockCommand(bike.getQrCode());

        ride = rideRepository.save(ride);

        return mapToDto(ride);
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371; // Earth radius in km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    public RideDto mapToDto(Ride ride) {
        return RideDto.builder()
                .id(ride.getId())
                .userId(ride.getUser().getId())
                .bikeId(ride.getBike().getId())
                .bikeQrCode(ride.getBike().getQrCode())
                .startTime(ride.getStartTime())
                .endTime(ride.getEndTime())
                .startLatitude(ride.getStartLatitude())
                .startLongitude(ride.getStartLongitude())
                .endLatitude(ride.getEndLatitude())
                .endLongitude(ride.getEndLongitude())
                .distance(ride.getDistance())
                .cost(ride.getCost())
                .active(ride.isActive())
                .build();
    }
}
