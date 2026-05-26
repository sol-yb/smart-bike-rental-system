package com.smartbike.rental.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invoice {
    private String invoiceNumber;
    private UUID rideId;
    private String email;
    private String firstName;
    private String lastName;
    private String bikeQrCode;
    
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private long durationMinutes;
    private double distanceKm;
    
    private BigDecimal baseFare;
    private BigDecimal durationCharge;
    private BigDecimal distanceCharge;
    
    private BigDecimal surgeSurcharge; // Rush hour + Weather
    private BigDecimal batteryDiscount;
    private BigDecimal promoDiscount;
    
    private BigDecimal subTotal;
    private BigDecimal taxAmount; // 15% VAT
    private BigDecimal totalFare;
    
    private String paymentStatus;
    private String transactionReference;
}
