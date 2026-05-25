package com.smartbike.rental.service;

import com.smartbike.rental.exception.BadRequestException;
import com.smartbike.rental.exception.ResourceNotFoundException;
import com.smartbike.rental.model.Payment;
import com.smartbike.rental.model.Ride;
import com.smartbike.rental.model.User;
import com.smartbike.rental.repository.PaymentRepository;
import com.smartbike.rental.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private NotificationService notificationService;

    @Transactional
    public User topUpWallet(UUID userId, BigDecimal amount, String method) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Top-up amount must be positive");
        }

        user.setWalletBalance(user.getWalletBalance().add(amount));
        user = userRepository.save(user);

        Payment payment = Payment.builder()
                .user(user)
                .amount(amount)
                .status("SUCCESS")
                .paymentMethod(method)
                .transactionReference("TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .build();
        paymentRepository.save(payment);

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

        user.setWalletBalance(user.getWalletBalance().subtract(cost));
        userRepository.save(user);

        Payment payment = Payment.builder()
                .user(user)
                .ride(ride)
                .amount(cost)
                .status("SUCCESS")
                .paymentMethod("WALLET")
                .transactionReference("RIDE-" + ride.getId().toString().substring(0, 8).toUpperCase())
                .build();
        paymentRepository.save(payment);

        notificationService.createNotification(
                user,
                "Ride Payment Charged",
                "Your ride on bike " + ride.getBike().getQrCode() + " has completed. Charged: " + cost + " birr. Remaining balance: " + user.getWalletBalance() + " birr.",
                "PAYMENT_ALERT"
        );
    }

    public List<Payment> getPaymentsByUser(UUID userId) {
        return paymentRepository.findByUserId(userId);
    }
}
