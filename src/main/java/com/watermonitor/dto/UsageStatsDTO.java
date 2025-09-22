package com.watermonitor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageStatsDTO {
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private double totalGallons;
    private double averageDailyUsage;
    private double peakDailyUsage;
    private double lowestDailyUsage;
    private double currentReading;
    private double previousReading;
    private double percentageChange;
    private String period;
}