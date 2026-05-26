package com.smartbike.rental.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

public class FareCalculator {

    // Base fee: 5.0 birr
    private static final BigDecimal BASE_FARE = BigDecimal.valueOf(5.0);
    // Rate per minute: 0.5 birr
    private static final BigDecimal DURATION_RATE = BigDecimal.valueOf(0.5);
    // Rate per kilometer: 2.0 birr
    private static final BigDecimal DISTANCE_RATE = BigDecimal.valueOf(2.0);

    public static BigDecimal calculateRawFare(long durationMinutes, double distanceKm) {
        if (durationMinutes < 1) durationMinutes = 1; // Minimum charge 1 minute
        
        BigDecimal durationCost = DURATION_RATE.multiply(BigDecimal.valueOf(durationMinutes));
        BigDecimal distanceCost = DISTANCE_RATE.multiply(BigDecimal.valueOf(distanceKm));
        
        return BASE_FARE.add(durationCost).add(distanceCost);
    }

    public static double getRushHourSurgeMultiplier(LocalDateTime dateTime) {
        int hour = dateTime.getHour();
        int dayOfWeek = dateTime.getDayOfWeek().getValue(); // 1 = Monday, 7 = Sunday
        
        // Rush hour on weekdays: 7:00 - 9:00 AM (7, 8) and 4:00 - 7:00 PM (16, 17, 18)
        if (dayOfWeek >= 1 && dayOfWeek <= 5) {
            if ((hour >= 7 && hour < 9) || (hour >= 16 && hour < 19)) {
                return 1.5; // 50% surge
            }
        }
        return 1.0;
    }

    public static double getWeatherSurgeMultiplier(String weatherCondition) {
        if (weatherCondition == null) return 1.0;
        
        return switch (weatherCondition.toUpperCase()) {
            case "RAINY" -> 1.3;
            case "STORMY" -> 1.6;
            case "EXTREME_HEAT" -> 1.25;
            default -> 1.0;
        };
    }

    public static double getBatteryDiscountMultiplier(int batteryLevel) {
        // Incentivize users to use bikes with low battery (< 30% battery)
        if (batteryLevel > 0 && batteryLevel < 30) {
            return 0.85; // 15% discount
        }
        return 1.0;
    }

    public static BigDecimal applyCoupons(BigDecimal totalFare, String couponType, BigDecimal couponValue) {
        if (couponType == null || couponValue == null || couponValue.compareTo(BigDecimal.ZERO) <= 0) {
            return totalFare;
        }

        BigDecimal discount = BigDecimal.ZERO;
        if ("PERCENTAGE".equalsIgnoreCase(couponType)) {
            // value e.g. 20 means 20%
            BigDecimal percentage = couponValue.divide(BigDecimal.valueOf(100.0), 4, RoundingMode.HALF_UP);
            discount = totalFare.multiply(percentage);
        } else if ("FIXED".equalsIgnoreCase(couponType)) {
            discount = couponValue;
        }

        BigDecimal finalFare = totalFare.subtract(discount);
        if (finalFare.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        return finalFare.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal calculateFinalFare(
            long durationMinutes, 
            double distanceKm, 
            LocalDateTime startTime, 
            String weatherCondition, 
            int initialBatteryLevel,
            String couponType,
            BigDecimal couponValue) {
        
        BigDecimal rawFare = calculateRawFare(durationMinutes, distanceKm);
        
        double rushSurge = getRushHourSurgeMultiplier(startTime);
        double weatherSurge = getWeatherSurgeMultiplier(weatherCondition);
        double batteryDiscount = getBatteryDiscountMultiplier(initialBatteryLevel);
        
        // Multiplier compound calculation
        BigDecimal multipliedFare = rawFare
                .multiply(BigDecimal.valueOf(rushSurge))
                .multiply(BigDecimal.valueOf(weatherSurge))
                .multiply(BigDecimal.valueOf(batteryDiscount));
        
        BigDecimal finalFare = applyCoupons(multipliedFare, couponType, couponValue);
        return finalFare.setScale(2, RoundingMode.HALF_UP);
    }
}
