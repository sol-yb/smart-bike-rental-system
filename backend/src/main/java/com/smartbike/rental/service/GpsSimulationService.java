package com.smartbike.rental.service;

import com.smartbike.rental.model.*;
import com.smartbike.rental.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class GpsSimulationService {

    @Autowired
    private GpsLogRepository gpsLogRepository;

    @Autowired
    private RideRepository rideRepository;

    @Autowired
    private GeofenceViolationRepository geofenceViolationRepository;

    @Autowired
    private UnsafeDrivingRepository unsafeDrivingRepository;

    @Autowired
    @Lazy
    private GeofenceService geofenceService;

    @Autowired
    @Lazy
    private NotificationService notificationService;

    @Autowired
    @Lazy
    private MqttService mqttService;

    // In-memory cache of the last speed for acceleration calculus
    private final Map<UUID, Double> lastSpeeds = new HashMap<>();

    @Transactional
    public void processGpsPing(Bike bike, double lat, double lon) {
        // 1. Fetch active ride for this bike (if any)
        Ride activeRide = rideRepository.findByBikeIdAndActive(bike.getId(), true).orElse(null);

        // 2. Fetch the previous coordinate log to compute speed/acceleration
        List<GpsLog> pastLogs = gpsLogRepository.findByBikeIdOrderByCreatedAtDesc(bike.getId());
        GpsLog lastLog = pastLogs.isEmpty() ? null : pastLogs.get(0);

        double speedKmh = 0.0;
        
        if (lastLog != null) {
            double distanceKm = calculateDistance(lastLog.getLatitude(), lastLog.getLongitude(), lat, lon);
            long secondsElapsed = Duration.between(lastLog.getCreatedAt(), LocalDateTime.now()).toSeconds();
            
            if (secondsElapsed > 0) {
                // speed = distance / time
                speedKmh = (distanceKm / (secondsElapsed / 3600.0));
                
                // Unsafe speeding check (bikes shouldn't exceed 25.0 km/h)
                if (speedKmh > 25.0 && activeRide != null) {
                    UnsafeDrivingAlert alert = UnsafeDrivingAlert.builder()
                            .ride(activeRide)
                            .alertType("SPEEDING")
                            .value(speedKmh)
                            .latitude(lat)
                            .longitude(lon)
                            .build();
                    unsafeDrivingRepository.save(alert);
                    
                    notificationService.createNotification(
                            activeRide.getUser(),
                            "Safety Alert: Speeding Detected",
                            String.format("You are riding at %.1f km/h. Please slow down for your safety.", speedKmh),
                            "SAFETY_ALERT"
                    );
                }

                // Acceleration / Deceleration calculus (m/s^2)
                Double lastSpeed = lastSpeeds.get(bike.getId());
                if (lastSpeed != null) {
                    double speedMs = speedKmh / 3.6;
                    double lastSpeedMs = lastSpeed / 3.6;
                    double accel = (speedMs - lastSpeedMs) / secondsElapsed;
                    
                    if (accel > 4.0 && activeRide != null) { // 4 m/s^2 rapid acceleration
                        unsafeDrivingRepository.save(UnsafeDrivingAlert.builder()
                                .ride(activeRide)
                                .alertType("RAPID_ACCELERATION")
                                .value(accel)
                                .latitude(lat)
                                .longitude(lon)
                                .build());
                    } else if (accel < -4.0 && activeRide != null) { // rapid deceleration
                        unsafeDrivingRepository.save(UnsafeDrivingAlert.builder()
                                .ride(activeRide)
                                .alertType("RAPID_DECELERATION")
                                .value(accel)
                                .latitude(lat)
                                .longitude(lon)
                                .build());
                    }
                }
            }
        }

        lastSpeeds.put(bike.getId(), speedKmh);

        // 3. Perform geofencing check
        // We'll wrap around geofenceService check, but also save violation logs!
        boolean inside = isInsideBoundary(lat, lon);
        if (!inside) {
            if (activeRide != null) {
                // Log geofence violation
                GeofenceViolation violation = GeofenceViolation.builder()
                        .ride(activeRide)
                        .bike(bike)
                        .latitude(lat)
                        .longitude(lon)
                        .build();
                geofenceViolationRepository.save(violation);
                
                // Enforce safety lock
                mqttService.publishLockCommand(bike.getQrCode());
                
                notificationService.createNotification(
                        activeRide.getUser(),
                        "Geofence Violation Triggered",
                        "You crossed the campus boundary on bike " + bike.getQrCode() + ". The bike has been automatically locked.",
                        "SYSTEM_ALERT"
                );
            } else {
                // Log theft warning geofence violation
                geofenceViolationRepository.save(GeofenceViolation.builder()
                        .bike(bike)
                        .latitude(lat)
                        .longitude(lon)
                        .build());
            }
        }
    }

    public List<Map<String, Object>> getPlaybackCoordinates(UUID rideId) {
        Ride ride = rideRepository.findById(rideId).orElse(null);
        if (ride == null) return Collections.emptyList();
        
        LocalDateTime start = ride.getStartTime();
        LocalDateTime end = ride.getEndTime() != null ? ride.getEndTime() : LocalDateTime.now();
        
        List<GpsLog> logs = gpsLogRepository.findByBikeIdAndCreatedAtBetweenOrderByCreatedAtAsc(
                ride.getBike().getId(), start, end
        );
        
        List<Map<String, Object>> playbackList = new ArrayList<>();
        GpsLog prev = null;
        
        for (GpsLog log : logs) {
            Map<String, Object> point = new HashMap<>();
            point.put("latitude", log.getLatitude());
            point.put("longitude", log.getLongitude());
            point.put("timestamp", log.getCreatedAt().toString());
            
            double speed = 0.0;
            if (prev != null) {
                double dist = calculateDistance(prev.getLatitude(), prev.getLongitude(), log.getLatitude(), log.getLongitude());
                long sec = Duration.between(prev.getCreatedAt(), log.getCreatedAt()).toSeconds();
                if (sec > 0) {
                    speed = dist / (sec / 3600.0);
                }
            }
            point.put("speedKmh", Math.round(speed * 10.0) / 10.0);
            playbackList.add(point);
            prev = log;
        }
        
        return playbackList;
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371; // km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private boolean isInsideBoundary(double lat, double lon) {
        // Addis Ababa generic rectangular boundary coordinate checks (matching GeofenceService)
        // Lat: 9.030 to 9.045, Lon: 38.750 to 38.765
        return (lat >= 9.030 && lat <= 9.045 && lon >= 38.750 && lon <= 38.765);
    }
}
