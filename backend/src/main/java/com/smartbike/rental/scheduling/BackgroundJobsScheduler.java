package com.smartbike.rental.scheduling;

import com.smartbike.rental.model.*;
import com.smartbike.rental.repository.*;
import com.smartbike.rental.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class BackgroundJobsScheduler {

    @Autowired
    private RideRepository rideRepository;

    @Autowired
    private BikeRepository bikeRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private RideService rideService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private AuditLogService auditLogService;

    /**
     * Clean up expired rides (rides running for > 12 hours)
     * Runs every 10 minutes
     */
    @Scheduled(fixedDelay = 600000) // 10 minutes
    @Transactional
    public void cleanupExpiredRides() {
        System.out.println("[CRON] Running expired rides cleanup job...");
        LocalDateTime threshold = LocalDateTime.now().minusHours(12);
        List<Ride> activeRides = rideRepository.findByActive(true);

        int count = 0;
        for (Ride ride : activeRides) {
            if (ride.getStartTime().isBefore(threshold)) {
                try {
                    // Auto-end ride at start coordinates to gracefully resolve it
                    rideService.endRide(ride.getId(), ride.getStartLatitude(), ride.getStartLongitude());
                    
                    notificationService.createNotification(
                            ride.getUser(),
                            "Ride Auto-Ended",
                            "Your ride exceeded the 12-hour duration limit and has been automatically completed by the system.",
                            "SYSTEM_ALERT"
                    );
                    
                    auditLogService.log(
                            ride.getUser().getId(),
                            ride.getUser().getEmail(),
                            "RIDE_AUTO_EXPIRED",
                            "SYSTEM",
                            "Ride ID " + ride.getId() + " auto-ended due to exceeding 12-hour limit."
                    );
                    count++;
                } catch (Exception e) {
                    System.err.println("[CRON] Failed to auto-end ride: " + ride.getId() + " | Error: " + e.getMessage());
                }
            }
        }
        if (count > 0) {
            System.out.println("[CRON] Successfully completed " + count + " expired rides.");
        }
    }

    /**
     * Retry failed ride payments (when wallet balance has been topped up to positive)
     * Runs every 15 minutes
     */
    @Scheduled(fixedDelay = 900000) // 15 minutes
    @Transactional
    public void retryFailedPayments() {
        System.out.println("[CRON] Running payment retry job...");
        List<Payment> failedPayments = paymentRepository.findAll().stream()
                .filter(p -> "FAILED".equalsIgnoreCase(p.getStatus()))
                .toList();

        int count = 0;
        for (Payment payment : failedPayments) {
            User user = payment.getUser();
            if (user.getWalletBalance().compareTo(BigDecimal.ZERO) >= 0) {
                // User has settled their balance, mark the payment as SUCCESS
                payment.setStatus("SUCCESS");
                paymentRepository.save(payment);
                
                auditLogService.log(
                        user.getId(),
                        user.getEmail(),
                        "PAYMENT_RECOVERED",
                        "SYSTEM",
                        "Failed payment ID " + payment.getId() + " successfully recovered via cron scheduler."
                );
                
                notificationService.createNotification(
                        user,
                        "Outstanding Balance Settled",
                        "Your outstanding negative balance has been successfully resolved. Thank you!",
                        "PAYMENT_ALERT"
                );
                count++;
            }
        }
        if (count > 0) {
            System.out.println("[CRON] Successfully settled " + count + " outstanding payments.");
        }
    }

    /**
     * Detect inactive bikes (no status update or GPS pings in over 6 hours)
     * Runs every hour
     */
    @Scheduled(fixedDelay = 3600000) // 1 hour
    @Transactional
    public void detectInactiveBikes() {
        System.out.println("[CRON] Running inactive bike detection job...");
        LocalDateTime threshold = LocalDateTime.now().minusHours(6);
        List<Bike> bikes = bikeRepository.findAll();

        int count = 0;
        for (Bike bike : bikes) {
            LocalDateTime lastUpdate = bike.getUpdatedAt() != null ? bike.getUpdatedAt() : bike.getCreatedAt();
            if (lastUpdate.isBefore(threshold) && bike.getState() != BikeState.IN_USE) {
                // If it is in AVAILABLE state, set to maintenance/offline
                if (bike.getState() == BikeState.AVAILABLE) {
                    bike.setState(BikeState.OUT_OF_SERVICE);
                    bikeRepository.save(bike);
                    
                    notificationService.createNotification(
                            null,
                            "Maintenance Alert: Offline Bike",
                            "Bike " + bike.getQrCode() + " has been offline/inactive for > 6 hours. Status set to OUT_OF_SERVICE.",
                            "THEFT_ALERT"
                    );
                    
                    auditLogService.log(
                            null,
                            "SYSTEM",
                            "BIKE_OFFLINE_MAINTENANCE",
                            "SYSTEM",
                            "Bike " + bike.getQrCode() + " set to OUT_OF_SERVICE due to lack of MQTT pings for 6 hours."
                    );
                    count++;
                }
            }
        }
        if (count > 0) {
            System.out.println("[CRON] Flagged " + count + " inactive bikes for maintenance.");
        }
    }

    /**
     * Alert operators of low battery bikes (< 20% battery level)
     * Runs every 30 minutes
     */
    @Scheduled(fixedDelay = 1800000) // 30 minutes
    public void batteryAlertScheduler() {
        System.out.println("[CRON] Running battery level threshold check...");
        List<Bike> bikes = bikeRepository.findAll().stream()
                .filter(b -> b.getBatteryLevel() < 20 && b.getState() == BikeState.AVAILABLE)
                .toList();

        for (Bike bike : bikes) {
            notificationService.createNotification(
                    null,
                    "Low Battery Warning: " + bike.getQrCode(),
                    "Bike " + bike.getQrCode() + " is currently available but battery level is critically low at " + bike.getBatteryLevel() + "%. Please schedule a swap.",
                    "THEFT_ALERT"
            );
            System.out.println("[CRON] Dispatched low battery maintenance alert for Bike: " + bike.getQrCode());
        }
    }
}
