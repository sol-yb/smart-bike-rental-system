package com.smartbike.rental.controller;

import com.smartbike.rental.dto.Invoice;
import com.smartbike.rental.dto.PaymentRequest;
import com.smartbike.rental.model.Payment;
import com.smartbike.rental.model.User;
import com.smartbike.rental.service.AuthService;
import com.smartbike.rental.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private AuthService authService;

    private User getAuthenticatedUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return authService.getUserByEmail(email);
    }

    @PostMapping("/topup")
    public ResponseEntity<User> topUpWallet(@Valid @RequestBody PaymentRequest request) {
        User user = getAuthenticatedUser();
        return ResponseEntity.ok(paymentService.topUpWallet(user.getId(), request.getAmount(), request.getPaymentMethod()));
    }

    @GetMapping("/history")
    public ResponseEntity<List<Payment>> getPaymentHistory() {
        User user = getAuthenticatedUser();
        return ResponseEntity.ok(paymentService.getPaymentsByUser(user.getId()));
    }

    @GetMapping("/invoice/{rideId}")
    public ResponseEntity<Invoice> getInvoice(@PathVariable UUID rideId) {
        return ResponseEntity.ok(paymentService.generateInvoice(rideId));
    }

    @PostMapping("/refund/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Payment> refundPayment(@PathVariable UUID id) {
        return ResponseEntity.ok(paymentService.processRefund(id));
    }

    @PostMapping("/rollback/{rideId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> rollbackPayment(@PathVariable UUID rideId) {
        paymentService.rollbackRidePayment(rideId);
        return ResponseEntity.ok("Transaction successfully rolled back");
    }
}
