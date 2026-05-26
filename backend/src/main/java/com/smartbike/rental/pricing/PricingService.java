package com.smartbike.rental.pricing;

import com.smartbike.rental.model.Bike;
import com.smartbike.rental.model.Coupon;
import com.smartbike.rental.model.Ride;
import com.smartbike.rental.repository.CouponRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class PricingService {

    @Autowired
    private CouponRepository couponRepository;

    private String currentMockWeather = "SUNNY";

    public String getCurrentWeather() {
        return currentMockWeather;
    }

    public void setMockWeather(String weather) {
        if (weather != null) {
            this.currentMockWeather = weather.toUpperCase();
        }
    }

    public BigDecimal calculateRideFare(Ride ride, String couponCode) {
        LocalDateTime start = ride.getStartTime();
        LocalDateTime end = ride.getEndTime() != null ? ride.getEndTime() : LocalDateTime.now();
        
        long minutes = Duration.between(start, end).toMinutes();
        if (minutes < 1) minutes = 1;
        
        double distance = ride.getDistance() != null ? ride.getDistance() : 0.0;
        Bike bike = ride.getBike();
        int initialBattery = bike != null ? bike.getBatteryLevel() : 100;
        
        String couponType = null;
        BigDecimal couponValue = null;
        
        if (couponCode != null && !couponCode.trim().isEmpty()) {
            Coupon coupon = couponRepository.findByCodeIgnoreCase(couponCode).orElse(null);
            if (coupon != null && coupon.isActive() && coupon.getExpiryDate().isAfter(LocalDateTime.now()) && coupon.getUsedCount() < coupon.getMaxUses()) {
                couponType = coupon.getDiscountType();
                couponValue = coupon.getDiscountValue();
            }
        }
        
        return FareCalculator.calculateFinalFare(
                minutes,
                distance,
                start,
                currentMockWeather,
                initialBattery,
                couponType,
                couponValue
        );
    }
}
