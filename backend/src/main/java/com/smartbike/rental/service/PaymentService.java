package com.smartbike.rental.service;

import com.smartbike.rental.dto.Invoice;
import com.smartbike.rental.exception.BadRequestException;
import com.smartbike.rental.exception.ResourceNotFoundException;
import com.smartbike.rental.model.Payment;
import com.smartbike.rental.model.Ride;
import com.smartbike.rental.model.User;
import com.smartbike.rental.repository.PaymentRepository;
import com.smartbike.rental.repository.UserRepository;
import com.smartbike.rental.repository.RideRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private RideRepository rideRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private AuditLogService auditLogService;

    @Transactional
    public User topUpWallet(UUID userId, BigDecimal amount, String method) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Top-up amount must be positive");
        }

        BigDecimal oldBalance = user.getWalletBalance();
        BigDecimal newBalance = oldBalance.add(amount);
        user.setWalletBalance(newBalance);
        user = userRepository.save(user);

        // 1. Log top up payment transaction
        Payment payment = Payment.builder()
                .user(user)
                .amount(amount)
                .status("SUCCESS")
                .paymentMethod(method)
                .transactionReference("TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .build();
        paymentRepository.save(payment);

        auditLogService.log(user.getId(), user.getEmail(), "WALLET_TOPUP", null, 
                String.format("Topped up wallet with %s birr via %s. Old balance: %s, New balance: %s", amount, method, oldBalance, newBalance));

        // 2. Failed Payment Recovery: If user had a failed payment due to negative balance, settle it!
        if (oldBalance.compareTo(BigDecimal.ZERO) < 0 && newBalance.compareTo(BigDecimal.ZERO) >= 0) {
            List<Payment> failedPayments = paymentRepository.findByUserId(userId).stream()
                    .filter(p -> "FAILED".equalsIgnoreCase(p.getStatus()))
                    .toList();

            for (Payment failedPay : failedPayments) {
                failedPay.setStatus("SUCCESS");
                paymentRepository.save(failedPay);
                auditLogService.log(user.getId(), user.getEmail(), "PAYMENT_RECOVERED", null, 
                        "Auto-recovered failed ride payment. Settle Reference: " + failedPay.getTransactionReference());
            }
        }

        notificationService.createNotification(
                user,
                "Wallet Credited",
                "Your wallet has been credited with " + amount + " birr. New balance: " + user.getWalletBalance() + " birr.",
                "PAYMENT_ALERT"
        );

        return user;
    }

    @Transactional
    public void processRidePayment(Ride ride) {
        User user = ride.getUser();
        BigDecimal cost = ride.getCost();

        if (cost.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }

        BigDecimal originalBalance = user.getWalletBalance();
        BigDecimal finalBalance = originalBalance.subtract(cost);
        user.setWalletBalance(finalBalance);
        userRepository.save(user);

        // Determine if payment fails due to negative balance or succeeds
        String status = finalBalance.compareTo(BigDecimal.ZERO) >= 0 ? "SUCCESS" : "FAILED";

        Payment payment = Payment.builder()
                .user(user)
                .ride(ride)
                .amount(cost)
                .status(status)
                .paymentMethod("WALLET")
                .transactionReference("RIDE-" + ride.getId().toString().substring(0, 8).toUpperCase())
                .build();
        paymentRepository.save(payment);

        auditLogService.log(user.getId(), user.getEmail(), "RIDE_PAYMENT", null, 
                String.format("Processed ride payment. Status: %s | Cost: %s | Old Balance: %s | New Balance: %s", 
                        status, cost, originalBalance, finalBalance));

        if ("FAILED".equalsIgnoreCase(status)) {
            notificationService.createNotification(
                    user,
                    "Payment Surcharged (Negative Balance)",
                    "Your ride completed, but your wallet balance went negative (" + finalBalance + " birr). Please top up to reactivate standard usage privileges.",
                    "PAYMENT_ALERT"
            );
        } else {
            notificationService.createNotification(
                    user,
                    "Ride Payment Charged",
                    "Your ride on bike " + ride.getBike().getQrCode() + " has completed. Charged: " + cost + " birr. Remaining balance: " + user.getWalletBalance() + " birr.",
                    "PAYMENT_ALERT"
            );
        }
    }

    @Transactional
    public void rollbackRidePayment(UUID rideId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new ResourceNotFoundException("Ride not found"));
        
        Payment payment = paymentRepository.findByUserId(ride.getUser().getId()).stream()
                .filter(p -> p.getRide() != null && p.getRide().getId().equals(rideId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("No payment found for ride: " + rideId));

        if ("ROLLED_BACK".equalsIgnoreCase(payment.getStatus())) {
            throw new BadRequestException("Transaction is already rolled back.");
        }

        User user = ride.getUser();
        BigDecimal refundAmount = payment.getAmount();
        
        user.setWalletBalance(user.getWalletBalance().add(refundAmount));
        userRepository.save(user);

        payment.setStatus("ROLLED_BACK");
        paymentRepository.save(payment);

        auditLogService.log(user.getId(), user.getEmail(), "PAYMENT_ROLLBACK", null, 
                "Rolled back ride booking charge of " + refundAmount + " birr for Ride: " + rideId);
    }

    @Transactional
    public Payment processRefund(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        if (!"SUCCESS".equalsIgnoreCase(payment.getStatus())) {
            throw new BadRequestException("Only successful payments can be refunded. Current status: " + payment.getStatus());
        }

        User user = payment.getUser();
        BigDecimal amount = payment.getAmount();

        user.setWalletBalance(user.getWalletBalance().add(amount));
        userRepository.save(user);

        payment.setStatus("REFUNDED");
        payment = paymentRepository.save(payment);

        auditLogService.log(user.getId(), user.getEmail(), "PAYMENT_REFUNDED", null, 
                "Refunded " + amount + " birr to user for Payment ID: " + paymentId);

        notificationService.createNotification(
                user,
                "Payment Refunded",
                "A refund of " + amount + " birr has been processed to your wallet. New balance: " + user.getWalletBalance() + " birr.",
                "PAYMENT_ALERT"
        );

        return payment;
    }

    public Invoice generateInvoice(UUID rideId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new ResourceNotFoundException("Ride not found"));

        Payment payment = paymentRepository.findAll().stream()
                .filter(p -> p.getRide() != null && p.getRide().getId().equals(rideId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("No payment found for ride"));

        long minutes = Duration.between(ride.getStartTime(), ride.getEndTime() != null ? ride.getEndTime() : LocalDateTime.now()).toMinutes();
        if (minutes < 1) minutes = 1;

        double distance = ride.getDistance();
        
        BigDecimal base = BigDecimal.valueOf(5.0);
        BigDecimal durationCost = BigDecimal.valueOf(0.5).multiply(BigDecimal.valueOf(minutes));
        BigDecimal distanceCost = BigDecimal.valueOf(2.0).multiply(BigDecimal.valueOf(distance));
        BigDecimal subTotal = base.add(durationCost).add(distanceCost);
        
        // Dynamic factors estimation
        BigDecimal total = ride.getCost();
        BigDecimal extras = total.subtract(subTotal);
        BigDecimal surge = extras.compareTo(BigDecimal.ZERO) > 0 ? extras : BigDecimal.ZERO;
        BigDecimal discount = extras.compareTo(BigDecimal.ZERO) < 0 ? extras.abs() : BigDecimal.ZERO;
        
        BigDecimal vat = total.multiply(BigDecimal.valueOf(0.15)).setScale(2, RoundingMode.HALF_UP); // 15% VAT

        return Invoice.builder()
                .invoiceNumber("INV-" + ride.getId().toString().substring(0, 8).toUpperCase())
                .rideId(ride.getId())
                .email(ride.getUser().getEmail())
                .firstName(ride.getUser().getFirstName())
                .lastName(ride.getUser().getLastName())
                .bikeQrCode(ride.getBike().getQrCode())
                .startTime(ride.getStartTime())
                .endTime(ride.getEndTime())
                .durationMinutes(minutes)
                .distanceKm(distance)
                .baseFare(base)
                .durationCharge(durationCost)
                .distanceCharge(distanceCost)
                .surgeSurcharge(surge)
                .batteryDiscount(BigDecimal.ZERO) // Included in compounds
                .promoDiscount(discount)
                .subTotal(subTotal)
                .taxAmount(vat)
                .totalFare(total)
                .paymentStatus(payment.getStatus())
                .transactionReference(payment.getTransactionReference())
                .build();
    }

    public List<Payment> getPaymentsByUser(UUID userId) {
        return paymentRepository.findByUserId(userId);
    }
}
