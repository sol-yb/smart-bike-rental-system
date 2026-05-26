package com.smartbike.rental.service;

import com.smartbike.rental.exception.BadRequestException;
import com.smartbike.rental.exception.ResourceNotFoundException;
import com.smartbike.rental.model.Coupon;
import com.smartbike.rental.repository.CouponRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
public class CouponService {

    @Autowired
    private CouponRepository couponRepository;

    public Coupon getCouponByCode(String code) {
        return couponRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon '" + code + "' not found"));
    }

    public boolean validateCoupon(String code) {
        if (code == null || code.trim().isEmpty()) {
            return false;
        }
        
        Coupon coupon = couponRepository.findByCodeIgnoreCase(code).orElse(null);
        if (coupon == null) {
            return false;
        }

        if (!coupon.isActive()) {
            return false;
        }

        if (coupon.getExpiryDate().isBefore(LocalDateTime.now())) {
            return false;
        }

        if (coupon.getUsedCount() >= coupon.getMaxUses()) {
            return false;
        }

        return true;
    }

    @Transactional
    public Coupon redeemCoupon(String code) {
        Coupon coupon = getCouponByCode(code);
        
        if (!validateCoupon(code)) {
            throw new BadRequestException("Coupon is invalid, expired, or fully redeemed");
        }

        coupon.setUsedCount(coupon.getUsedCount() + 1);
        return couponRepository.save(coupon);
    }

    @Transactional
    public Coupon createCoupon(String code, String discountType, double discountValue, int daysValid, int maxUses) {
        if (couponRepository.findByCodeIgnoreCase(code).isPresent()) {
            throw new BadRequestException("Coupon code already exists");
        }

        Coupon coupon = Coupon.builder()
                .code(code.toUpperCase())
                .discountType(discountType.toUpperCase())
                .discountValue(java.math.BigDecimal.valueOf(discountValue))
                .expiryDate(LocalDateTime.now().plusDays(daysValid))
                .maxUses(maxUses)
                .active(true)
                .build();

        return couponRepository.save(coupon);
    }
}
