package com.smartbike.rental.service;

import com.smartbike.rental.model.*;
import com.smartbike.rental.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@SuppressWarnings("unchecked")
public class AnalyticsService {

    @Autowired
    private RideRepository rideRepository;

    @Autowired
    private BikeRepository bikeRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private CacheService cacheService;

    private static final String CACHE_KEY = "analytics:dashboard";
    private static final long CACHE_TTL_SECONDS = 300; // 5 minutes cache

    public Map<String, Object> getDashboardStats() {
        // 1. Check cache first
        Map<String, Object> cachedStats = (Map<String, Object>) cacheService.get(CACHE_KEY);
        if (cachedStats != null) {
            System.out.println("[ANALYTICS] Returning cached dashboard statistics.");
            return cachedStats;
        }

        System.out.println("[ANALYTICS] Cache miss. Computing dashboard analytics...");
        Map<String, Object> stats = new HashMap<>();

        // 2. Fetch data
        List<Ride> allRides = rideRepository.findAll();
        List<Bike> allBikes = bikeRepository.findAll();
        List<Payment> allPayments = paymentRepository.findAll();

        // 3. Revenue calculations
        BigDecimal totalRevenue = allPayments.stream()
                .filter(p -> "SUCCESS".equalsIgnoreCase(p.getStatus()))
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        stats.put("totalRevenue", totalRevenue);

        // 4. Fleet metrics
        long totalBikes = allBikes.size();
        long inUseBikes = allBikes.stream().filter(b -> b.getState() == BikeState.IN_USE).count();
        double utilization = totalBikes > 0 ? ((double) inUseBikes / totalBikes) * 100.0 : 0.0;
        stats.put("totalBikes", totalBikes);
        stats.put("inUseBikes", inUseBikes);
        stats.put("utilizationPercentage", Math.round(utilization * 10.0) / 10.0);

        // 5. Peak Hours of Day Analysis
        Map<Integer, Long> hourFrequency = allRides.stream()
                .collect(Collectors.groupingBy(r -> r.getStartTime().getHour(), Collectors.counting()));
        
        List<Map<String, Object>> peakHours = hourFrequency.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> h = new HashMap<>();
                    h.put("hour", entry.getKey());
                    h.put("rideCount", entry.getValue());
                    return h;
                })
                .sorted((a, b) -> Long.compare((Long) b.get("rideCount"), (Long) a.get("rideCount")))
                .toList();
        stats.put("peakHours", peakHours);

        // 6. Top Hotspot Station Coordinates (rounding coordinates to 3 decimals to group stations)
        Map<String, Long> coordinateGroups = allRides.stream()
                .filter(r -> r.getStartLatitude() != null && r.getStartLongitude() != null)
                .collect(Collectors.groupingBy(r -> 
                        String.format("%.3f,%.3f", r.getStartLatitude(), r.getStartLongitude()), 
                        Collectors.counting()
                ));

        List<Map<String, Object>> topStations = coordinateGroups.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> station = new HashMap<>();
                    String[] coords = entry.getKey().split(",");
                    station.put("latitude", Double.parseDouble(coords[0]));
                    station.put("longitude", Double.parseDouble(coords[1]));
                    station.put("rentalsCount", entry.getValue());
                    return station;
                })
                .sorted((a, b) -> Long.compare((Long) b.get("rentalsCount"), (Long) a.get("rentalsCount")))
                .limit(5)
                .toList();
        stats.put("topStations", topStations);

        // 7. Predictive Demand Forecast
        // Formulates a demand score based on current hour, weekday, and top station activity
        List<Map<String, Object>> predictiveDemandList = new ArrayList<>();
        int currentHour = LocalDateTime.now().getHour();
        int dayOfWeek = LocalDateTime.now().getDayOfWeek().getValue();

        for (Map<String, Object> station : topStations) {
            double lat = (Double) station.get("latitude");
            double lon = (Double) station.get("longitude");
            long baselineRentals = (Long) station.get("rentalsCount");

            // Algorithmic multiplier based on rush hour
            double hourMultiplier = 1.0;
            if ((currentHour >= 7 && currentHour <= 9) || (currentHour >= 16 && currentHour <= 19)) {
                hourMultiplier = 1.8; // Peak times
            } else if (currentHour >= 22 || currentHour <= 5) {
                hourMultiplier = 0.3; // Night times
            }

            // Weekend vs Weekday adjustment
            double dayMultiplier = dayOfWeek <= 5 ? 1.2 : 0.8;

            double predictedScore = baselineRentals * hourMultiplier * dayMultiplier;
            String demandLevel = predictedScore > 20 ? "HIGH" : (predictedScore > 8 ? "MEDIUM" : "LOW");

            Map<String, Object> forecast = new HashMap<>();
            forecast.put("latitude", lat);
            forecast.put("longitude", lon);
            forecast.put("predictedHourlyDemandScore", Math.round(predictedScore * 10.0) / 10.0);
            forecast.put("demandLevel", demandLevel);
            predictiveDemandList.add(forecast);
        }
        stats.put("predictiveDemand", predictiveDemandList);

        // 8. Cache the results
        cacheService.put(CACHE_KEY, stats, CACHE_TTL_SECONDS);

        return stats;
    }
}
