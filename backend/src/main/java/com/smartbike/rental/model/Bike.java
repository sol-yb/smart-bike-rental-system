package com.smartbike.rental.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "bikes", indexes = {
    @Index(name = "idx_bikes_qr_code", columnList = "qr_code", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE bikes SET deleted = true WHERE id = ?")
@SQLRestriction("deleted = false")
public class Bike {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "qr_code", nullable = false, unique = true)
    private String qrCode;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Builder.Default
    @Column(name = "locked", nullable = false)
    private boolean locked = true;

    @Builder.Default
    @Column(name = "battery_level", nullable = false)
    private int batteryLevel = 100;

    @Builder.Default
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private BikeState state = BikeState.AVAILABLE;

    @Builder.Default
    @Column(nullable = false)
    private boolean deleted = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
