package com.smartbike.rental.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BookingRequest {
    @NotBlank(message = "QR Code is required")
    private String qrCode;
}
