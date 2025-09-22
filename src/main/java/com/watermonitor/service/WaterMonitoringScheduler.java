package com.watermonitor.service;

import com.watermonitor.model.WaterReading;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class WaterMonitoringScheduler {
    
    private final WaterMeterDataService waterMeterDataService;
    private final EmailService emailService;
    private final JavaMailSender mailSender;
    
    @Value("${email.recipient}")
    private String recipientEmail;
    
    @Value("${email.from}")
    private String fromEmail;
    
    // Flags to track if alerts have been sent (reset on app restart)
    private final AtomicBoolean abnormalUsageAlertSent = new AtomicBoolean(false);
    private final AtomicBoolean meterReadingFailureAlertSent = new AtomicBoolean(false);
    private final AtomicReference<ZonedDateTime> lastReadingTimestamp = new AtomicReference<>();
    private final AtomicReference<Double> lastReadingValue = new AtomicReference<>();
    private final AtomicReference<LocalDateTime> lastCheckTime = new AtomicReference<>();
    
    // Public getters for monitoring status
    public boolean isAbnormalUsageAlertSent() {
        return abnormalUsageAlertSent.get();
    }
    
    public boolean isMeterFailureAlertSent() {
        return meterReadingFailureAlertSent.get();
    }
    
    public ZonedDateTime getLastReadingTimestamp() {
        return lastReadingTimestamp.get();
    }
    
    public Double getLastReadingValue() {
        return lastReadingValue.get();
    }
    
    public LocalDateTime getLastCheckTime() {
        return lastCheckTime.get();
    }
    
    @PostConstruct
    public void init() {
        log.info("Water Monitoring Scheduler initialized");
        // Initialize with current reading
        updateLastReading();
    }
    
    /**
     * Send daily summary email at 9:00 PM every day
     */
    @Scheduled(cron = "0 0 21 * * ?") // 9:00 PM every day
    public void sendDailySummary() {
        log.info("Sending scheduled daily summary email at 9:00 PM");
        try {
            emailService.sendUsageSummary("day");
            log.info("Daily summary email sent successfully");
        } catch (Exception e) {
            log.error("Failed to send daily summary email", e);
        }
    }
    
    /**
     * Check for abnormal usage and meter reading issues every 10 minutes
     */
    @Scheduled(fixedDelay = 600000, initialDelay = 60000) // Every 10 minutes, with 1 minute initial delay
    public void monitorWaterUsage() {
        log.debug("Running water usage monitoring check");
        lastCheckTime.set(LocalDateTime.now());
        
        try {
            List<WaterReading> allReadings = waterMeterDataService.getAllReadings();
            
            if (allReadings.isEmpty()) {
                log.warn("No water readings available");
                return;
            }
            
            // Sort by timestamp
            allReadings.sort(Comparator.comparing(WaterReading::getTimestamp));
            
            // Get the latest reading
            WaterReading latestReading = allReadings.get(allReadings.size() - 1);
            ZonedDateTime currentTimestamp = latestReading.getTimestamp();
            double currentValue = latestReading.getGallons();
            
            // Check for meter reading failure
            checkMeterReadingFailure(currentTimestamp);
            
            // Check for abnormal usage
            checkAbnormalUsage(allReadings, currentTimestamp);
            
            // Update last reading info
            lastReadingTimestamp.set(currentTimestamp);
            lastReadingValue.set(currentValue);
            
        } catch (Exception e) {
            log.error("Error during water usage monitoring", e);
        }
    }
    
    /**
     * Check if meter readings have stopped (no new reading in last 30 minutes)
     */
    private void checkMeterReadingFailure(ZonedDateTime latestTimestamp) {
        ZonedDateTime lastKnown = lastReadingTimestamp.get();
        
        if (lastKnown != null) {
            // Check if the timestamp hasn't changed since last check
            if (latestTimestamp.isBefore(ZonedDateTime.now().minusMinutes(30))) {
                // No new reading in 30 minutes
                if (!meterReadingFailureAlertSent.get()) {
                    sendMeterFailureAlert(lastKnown);
                    meterReadingFailureAlertSent.set(true);
                    log.warn("Meter reading failure detected - no new readings since {}", lastKnown);
                }
            } else {
                // New reading detected, reset the flag
                if (meterReadingFailureAlertSent.get()) {
                    log.info("Meter readings resumed");
                    meterReadingFailureAlertSent.set(false);
                }
            }
        }
    }
    
    /**
     * Check for abnormal usage (more than 50 gallons in 30 minutes)
     */
    private void checkAbnormalUsage(List<WaterReading> readings, ZonedDateTime currentTime) {
        ZonedDateTime thirtyMinutesAgo = currentTime.minusMinutes(30);
        
        // Find readings in the last 30 minutes
        double startValue = -1;
        double endValue = -1;
        
        for (WaterReading reading : readings) {
            if (reading.getTimestamp().isAfter(thirtyMinutesAgo)) {
                if (startValue < 0) {
                    startValue = reading.getGallons();
                }
                endValue = reading.getGallons();
            }
        }
        
        if (startValue >= 0 && endValue >= 0) {
            double usage = endValue - startValue;
            
            if (usage > 50) {
                // Abnormal usage detected
                if (!abnormalUsageAlertSent.get()) {
                    sendAbnormalUsageAlert(usage, thirtyMinutesAgo, currentTime);
                    abnormalUsageAlertSent.set(true);
                    log.warn("Abnormal water usage detected: {} gallons in 30 minutes", usage);
                }
            } else {
                // Normal usage, reset flag for next day
                if (usage < 30 && abnormalUsageAlertSent.get()) {
                    // Only reset if usage has normalized significantly
                    log.info("Water usage normalized");
                    // Don't reset here - only reset on app restart or next day
                }
            }
        }
    }
    
    /**
     * Send alert for abnormal water usage
     */
    private void sendAbnormalUsageAlert(double gallonsUsed, ZonedDateTime startTime, ZonedDateTime endTime) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
            
            String subject = "⚠️ ALERT: Abnormal Water Usage Detected";
            
            StringBuilder body = new StringBuilder();
            body.append("WARNING: Abnormal water usage has been detected!\n\n");
            body.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");
            body.append(String.format("Usage: %.1f gallons in the last 30 minutes\n", gallonsUsed));
            body.append(String.format("Period: %s - %s\n", 
                startTime.format(formatter), 
                endTime.format(formatter)));
            body.append(String.format("Threshold: 50 gallons per 30 minutes\n"));
            body.append(String.format("Exceeded by: %.1f gallons\n\n", gallonsUsed - 50));
            body.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");
            body.append("RECOMMENDED ACTIONS:\n");
            body.append("• Check for running faucets or toilets\n");
            body.append("• Inspect for visible leaks\n");
            body.append("• Check if irrigation system is running\n");
            body.append("• Verify washing machine or dishwasher status\n\n");
            body.append("This alert will not repeat today unless the application is restarted.\n");
            body.append("Monitor your usage at: http://localhost:8080\n");
            
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(recipientEmail);
            message.setSubject(subject);
            message.setText(body.toString());
            
            mailSender.send(message);
            log.info("Abnormal usage alert email sent to: {}", recipientEmail);
            
        } catch (Exception e) {
            log.error("Failed to send abnormal usage alert email", e);
        }
    }
    
    /**
     * Send alert for meter reading failure
     */
    private void sendMeterFailureAlert(ZonedDateTime lastReadingTime) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            
            String subject = "⚠️ ALERT: Water Meter Reading Failure";
            
            StringBuilder body = new StringBuilder();
            body.append("WARNING: No new water meter readings detected!\n\n");
            body.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");
            body.append(String.format("Last successful reading: %s\n", 
                lastReadingTime.format(formatter)));
            body.append(String.format("Time since last reading: >30 minutes\n"));
            body.append(String.format("Current time: %s\n\n", 
                LocalDateTime.now().format(formatter)));
            body.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");
            body.append("POSSIBLE ISSUES:\n");
            body.append("• Meter reading process may have stopped\n");
            body.append("• File permissions issue with ~/bin/monitor.out\n");
            body.append("• Network or communication issue with meter\n");
            body.append("• Hardware failure or power issue\n\n");
            body.append("RECOMMENDED ACTIONS:\n");
            body.append("• Check if the meter reading process is running\n");
            body.append("• Verify file ~/bin/monitor.out is being updated\n");
            body.append("• Check system logs for errors\n");
            body.append("• Restart the meter reading service if needed\n\n");
            body.append("This alert will not repeat until readings resume and fail again.\n");
            
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(recipientEmail);
            message.setSubject(subject);
            message.setText(body.toString());
            
            mailSender.send(message);
            log.info("Meter failure alert email sent to: {}", recipientEmail);
            
        } catch (Exception e) {
            log.error("Failed to send meter failure alert email", e);
        }
    }
    
    /**
     * Reset daily alert flags at midnight (optional - can be used to reset alerts daily)
     */
    @Scheduled(cron = "0 0 0 * * ?") // Midnight every day
    public void resetDailyAlerts() {
        log.info("Resetting daily alert flags");
        abnormalUsageAlertSent.set(false);
        // Note: Not resetting meter failure alert as it should persist until fixed
    }
    
    /**
     * Helper method to update last reading info
     */
    private void updateLastReading() {
        try {
            List<WaterReading> readings = waterMeterDataService.getAllReadings();
            if (!readings.isEmpty()) {
                readings.sort(Comparator.comparing(WaterReading::getTimestamp));
                WaterReading latest = readings.get(readings.size() - 1);
                lastReadingTimestamp.set(latest.getTimestamp());
                lastReadingValue.set(latest.getGallons());
            }
        } catch (Exception e) {
            log.error("Error updating last reading", e);
        }
    }
}