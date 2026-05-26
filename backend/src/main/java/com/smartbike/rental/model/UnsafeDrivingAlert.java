package com.smartbike.rental.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "unsafe_driving_alerts", indexes = {
    @Index(name = "idx_unsafe_driving_alerts_ride_id", columnList = "ride_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnsafeDrivingAlert {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ride_id")
    private Ride ride;

    @Column(name = "alert_type", nullable = false)
    private String alertType; // "SPEEDING", "RAPID_ACCELERATION", "RAPID_DECELERATION"

    @Column(nullable = false)
    private Double value; // Speed in km/h or acceleration rate

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
