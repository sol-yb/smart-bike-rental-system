package com.smartbike.rental.dto;

import com.smartbike.rental.model.BikeState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BikeDto {
    private UUID id;
    private String qrCode;
    private Double latitude;
    private Double longitude;
    private boolean locked;
    private int batteryLevel;
    private BikeState state;
}
