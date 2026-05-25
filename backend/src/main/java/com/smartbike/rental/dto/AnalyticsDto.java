package com.smartbike.rental.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalyticsDto {
    private long totalBikes;
    private long activeRides;
    private long totalUsers;
    private BigDecimal totalRevenue;
    private long theftAlertsCount;
}
