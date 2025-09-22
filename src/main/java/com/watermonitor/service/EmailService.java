package com.watermonitor.service;

import com.watermonitor.model.WaterReading;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {
    
    private final JavaMailSender mailSender;
    private final WaterMeterDataService waterMeterDataService;
    
    @Value("${email.recipient}")
    private String recipientEmail;
    
    @Value("${email.from}")
    private String fromEmail;
    
    public void sendUsageSummary(String period) {
        try {
            String subject = "Water Usage Summary - " + period.toUpperCase();
            String body = generateEmailBody(period);
            
            sendEmail(subject, body);
            log.info("Email summary sent successfully for period: {}", period);
        } catch (Exception e) {
            log.error("Failed to send email summary", e);
            throw new RuntimeException("Failed to send email summary: " + e.getMessage());
        }
    }
    
    private String generateEmailBody(String period) {
        StringBuilder body = new StringBuilder();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        
        // Get all readings
        List<WaterReading> allReadings = waterMeterDataService.getAllReadings();
        if (allReadings.isEmpty()) {
            return "No water usage data available.";
        }
        
        // Sort readings by timestamp
        allReadings.sort(Comparator.comparing(WaterReading::getTimestamp));
        
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime startDate = getStartDateForPeriod(period, now);
        
        // Filter readings for the period
        List<WaterReading> periodReadings = allReadings.stream()
                .filter(r -> r.getTimestamp().isAfter(startDate))
                .collect(Collectors.toList());
        
        if (periodReadings.isEmpty()) {
            return "No water usage data available for the selected period.";
        }
        
        body.append("Water Usage Summary Report\n");
        body.append("===========================\n\n");
        body.append("Period: ").append(period.toUpperCase()).append("\n");
        body.append("Report Generated: ").append(LocalDateTime.now().format(formatter)).append("\n");
        body.append("From: ").append(startDate.format(formatter)).append("\n");
        body.append("To: ").append(now.format(formatter)).append("\n\n");
        
        // Calculate overall statistics
        double firstReading = periodReadings.get(0).getGallons();
        double lastReading = periodReadings.get(periodReadings.size() - 1).getGallons();
        double totalUsage = lastReading - firstReading;
        
        body.append("OVERALL STATISTICS\n");
        body.append("------------------\n");
        body.append(String.format("Total Usage: %,.1f gallons\n", totalUsage));
        body.append(String.format("Current Reading: %,.0f gallons\n", lastReading));
        body.append(String.format("Starting Reading: %,.0f gallons\n", firstReading));
        
        // Calculate hourly usage for last 24 hours
        if (period.equals("day") || period.equals("week")) {
            body.append("\n\nHOURLY USAGE (Last 24 Hours)\n");
            body.append("-----------------------------\n");
            
            ZonedDateTime oneDayAgo = now.minusHours(24);
            Map<LocalDateTime, Double> hourlyUsage = new TreeMap<>();
            
            for (WaterReading reading : periodReadings) {
                if (reading.getTimestamp().isAfter(oneDayAgo)) {
                    LocalDateTime roundedHour = reading.getTimestamp().toLocalDateTime().truncatedTo(ChronoUnit.HOURS);
                    hourlyUsage.put(roundedHour, reading.getGallons());
                }
            }
            
            Double previousValue = null;
            for (Map.Entry<LocalDateTime, Double> entry : hourlyUsage.entrySet()) {
                if (previousValue != null) {
                    double hourlyUse = entry.getValue() - previousValue;
                    body.append(String.format("%s -> %5.0f gallons/hour", 
                        entry.getKey().format(DateTimeFormatter.ofPattern("HH:mm")), 
                        hourlyUse));
                    
                    // Flag high usage
                    if (hourlyUse > 100) {
                        body.append(" **HIGH USAGE**");
                    }
                    body.append("\n");
                }
                previousValue = entry.getValue();
            }
        }
        
        // Calculate daily usage
        body.append("\n\nDAILY USAGE\n");
        body.append("-----------\n");
        
        Map<LocalDateTime, Double> dailyUsage = new TreeMap<>();
        for (WaterReading reading : periodReadings) {
            LocalDateTime roundedDay = reading.getTimestamp().toLocalDateTime().truncatedTo(ChronoUnit.DAYS);
            dailyUsage.put(roundedDay, reading.getGallons());
        }
        
        Double previousDayValue = null;
        double totalDailyUsage = 0;
        int dayCount = 0;
        
        for (Map.Entry<LocalDateTime, Double> entry : dailyUsage.entrySet()) {
            if (previousDayValue != null) {
                double dailyUse = entry.getValue() - previousDayValue;
                totalDailyUsage += dailyUse;
                dayCount++;
                body.append(String.format("%s -> %6.0f gallons\n", 
                    entry.getKey().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")), 
                    dailyUse));
            }
            previousDayValue = entry.getValue();
        }
        
        if (dayCount > 0) {
            double avgDailyUsage = totalDailyUsage / dayCount;
            body.append(String.format("\nAverage Daily Usage: %,.1f gallons\n", avgDailyUsage));
        }
        
        // Check for abnormal usage
        boolean hasAbnormalUsage = false;
        for (WaterReading reading : periodReadings) {
            // Check if any hourly usage exceeds 100 gallons (simplified check)
            // In production, you'd want more sophisticated anomaly detection
        }
        
        body.append("\n\n---\n");
        body.append("This is an automated report from the Water Usage Monitor system.\n");
        body.append("For questions or concerns, please check the dashboard at http://localhost:8080\n");
        
        return body.toString();
    }
    
    private ZonedDateTime getStartDateForPeriod(String period, ZonedDateTime now) {
        return switch (period.toLowerCase()) {
            case "day" -> now.minusDays(1);
            case "week" -> now.minusWeeks(1);
            case "month" -> now.minusMonths(1);
            case "ytd" -> now.withMonth(1).withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
            default -> now.minusDays(1);
        };
    }
    
    private void sendEmail(String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(recipientEmail);
        message.setSubject(subject);
        message.setText(body);
        
        mailSender.send(message);
        log.info("Email sent to: {}", recipientEmail);
    }
}