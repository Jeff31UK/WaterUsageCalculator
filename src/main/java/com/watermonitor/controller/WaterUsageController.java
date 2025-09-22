package com.watermonitor.controller;

import com.watermonitor.dto.ChartDataDTO;
import com.watermonitor.dto.UsageStatsDTO;
import com.watermonitor.dto.MonitoringStatusDTO;
import com.watermonitor.model.WaterReading;
import com.watermonitor.service.WaterMeterDataService;
import com.watermonitor.service.EmailService;
import com.watermonitor.service.WaterMonitoringScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/water")
@CrossOrigin(origins = "http://localhost:5173")
@RequiredArgsConstructor
public class WaterUsageController {
    
    private final WaterMeterDataService waterMeterDataService;
    private final EmailService emailService;
    private final WaterMonitoringScheduler monitoringScheduler;
    
    @GetMapping("/readings")
    public ResponseEntity<List<WaterReading>> getAllReadings() {
        return ResponseEntity.ok(waterMeterDataService.getAllReadings());
    }
    
    @GetMapping("/chart/{period}")
    public ResponseEntity<ChartDataDTO> getChartData(@PathVariable String period) {
        return ResponseEntity.ok(waterMeterDataService.getChartData(period));
    }
    
    @GetMapping("/stats/{period}")
    public ResponseEntity<UsageStatsDTO> getUsageStats(@PathVariable String period) {
        return ResponseEntity.ok(waterMeterDataService.getUsageStats(period));
    }
    
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Water Usage Monitor API is running");
    }
    
    @PostMapping("/email/{period}")
    public ResponseEntity<Map<String, String>> sendEmailSummary(@PathVariable String period) {
        Map<String, String> response = new HashMap<>();
        try {
            emailService.sendUsageSummary(period);
            response.put("status", "success");
            response.put("message", "Email summary sent successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Failed to send email: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    @GetMapping("/monitoring/status")
    public ResponseEntity<MonitoringStatusDTO> getMonitoringStatus() {
        MonitoringStatusDTO status = MonitoringStatusDTO.builder()
                .schedulerActive(true)
                .lastCheck(monitoringScheduler.getLastCheckTime())
                .nextDailySummary(java.time.LocalDateTime.now().withHour(21).withMinute(0).withSecond(0))
                .abnormalUsageAlertSent(monitoringScheduler.isAbnormalUsageAlertSent())
                .meterFailureAlertSent(monitoringScheduler.isMeterFailureAlertSent())
                .lastReadingTime(monitoringScheduler.getLastReadingTimestamp() != null ? 
                    monitoringScheduler.getLastReadingTimestamp().toString() : "N/A")
                .lastReadingValue(monitoringScheduler.getLastReadingValue())
                .status("Monitoring active - checking every 10 minutes, alerts after 30 minutes")
                .build();
        
        // Adjust next daily summary if it's already past 9 PM today
        if (java.time.LocalDateTime.now().getHour() >= 21) {
            status.setNextDailySummary(status.getNextDailySummary().plusDays(1));
        }
        
        return ResponseEntity.ok(status);
    }
}