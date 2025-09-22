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
public class MonitoringStatusDTO {
    private boolean schedulerActive;
    private LocalDateTime lastCheck;
    private LocalDateTime nextDailySummary;
    private boolean abnormalUsageAlertSent;
    private boolean meterFailureAlertSent;
    private String lastReadingTime;
    private Double lastReadingValue;
    private String status;
}