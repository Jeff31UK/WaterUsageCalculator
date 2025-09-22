package com.watermonitor.service;

import com.watermonitor.model.WaterReading;
import com.watermonitor.dto.UsageStatsDTO;
import com.watermonitor.dto.ChartDataDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class WaterMeterDataService {
    
    @Value("${water.meter.file.path}")
    private String filePath;
    
    public List<WaterReading> getAllReadings() {
        List<WaterReading> readings = new ArrayList<>();
        
        try {
            if (!Files.exists(Paths.get(filePath))) {
                log.warn("Water meter file not found at: {}", filePath);
                return generateMockData();
            }
            
            try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    try {
                        readings.add(WaterReading.fromCsvLine(line));
                    } catch (Exception e) {
                        log.warn("Failed to parse line: {}", line, e);
                    }
                }
            }
        } catch (IOException e) {
            log.error("Error reading water meter file", e);
            return generateMockData();
        }
        
        return readings;
    }
    
    public ChartDataDTO getChartData(String period) {
        List<WaterReading> allReadings = getAllReadings();
        if (allReadings.isEmpty()) {
            return ChartDataDTO.builder()
                    .dataPoints(new ArrayList<>())
                    .period(period)
                    .totalUsage(0)
                    .build();
        }
        
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime startDate = getStartDateForPeriod(period, now);
        
        List<WaterReading> filteredReadings = allReadings.stream()
                .filter(r -> r.getTimestamp().isAfter(startDate))
                .sorted(Comparator.comparing(WaterReading::getTimestamp))
                .collect(Collectors.toList());
        
        return buildChartData(filteredReadings, period);
    }
    
    public UsageStatsDTO getUsageStats(String period) {
        List<WaterReading> allReadings = getAllReadings();
        if (allReadings.isEmpty()) {
            return UsageStatsDTO.builder()
                    .period(period)
                    .totalGallons(0)
                    .averageDailyUsage(0)
                    .build();
        }
        
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime startDate = getStartDateForPeriod(period, now);
        
        List<WaterReading> periodReadings = allReadings.stream()
                .filter(r -> r.getTimestamp().isAfter(startDate))
                .sorted(Comparator.comparing(WaterReading::getTimestamp))
                .collect(Collectors.toList());
        
        if (periodReadings.isEmpty()) {
            return UsageStatsDTO.builder()
                    .period(period)
                    .totalGallons(0)
                    .averageDailyUsage(0)
                    .build();
        }
        
        double firstReading = periodReadings.get(0).getGallons();
        double lastReading = periodReadings.get(periodReadings.size() - 1).getGallons();
        double totalUsage = lastReading - firstReading;
        
        Map<LocalDate, Double> dailyUsage = calculateDailyUsage(periodReadings);
        double avgDaily = dailyUsage.values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0);
        
        double peakDaily = dailyUsage.values().stream()
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0);
        
        double lowestDaily = dailyUsage.values().stream()
                .mapToDouble(Double::doubleValue)
                .min()
                .orElse(0);
        
        // Calculate percentage change from previous period
        ZonedDateTime prevPeriodStart = getPreviousPeriodStart(period, startDate);
        List<WaterReading> prevPeriodReadings = allReadings.stream()
                .filter(r -> r.getTimestamp().isAfter(prevPeriodStart) && r.getTimestamp().isBefore(startDate))
                .sorted(Comparator.comparing(WaterReading::getTimestamp))
                .collect(Collectors.toList());
        
        double prevPeriodUsage = 0;
        if (!prevPeriodReadings.isEmpty()) {
            prevPeriodUsage = prevPeriodReadings.get(prevPeriodReadings.size() - 1).getGallons() 
                            - prevPeriodReadings.get(0).getGallons();
        }
        
        double percentageChange = prevPeriodUsage > 0 ? 
                ((totalUsage - prevPeriodUsage) / prevPeriodUsage) * 100 : 0;
        
        return UsageStatsDTO.builder()
                .startDate(startDate.toLocalDateTime())
                .endDate(now.toLocalDateTime())
                .totalGallons(totalUsage)
                .averageDailyUsage(avgDaily)
                .peakDailyUsage(peakDaily)
                .lowestDailyUsage(lowestDaily)
                .currentReading(lastReading)
                .previousReading(firstReading)
                .percentageChange(percentageChange)
                .period(period)
                .build();
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
    
    private ZonedDateTime getPreviousPeriodStart(String period, ZonedDateTime currentPeriodStart) {
        return switch (period.toLowerCase()) {
            case "day" -> currentPeriodStart.minusDays(1);
            case "week" -> currentPeriodStart.minusWeeks(1);
            case "month" -> currentPeriodStart.minusMonths(1);
            case "ytd" -> currentPeriodStart.minusYears(1);
            default -> currentPeriodStart.minusDays(1);
        };
    }
    
    private ChartDataDTO buildChartData(List<WaterReading> readings, String period) {
        if (readings.isEmpty()) {
            return ChartDataDTO.builder()
                    .dataPoints(new ArrayList<>())
                    .period(period)
                    .totalUsage(0)
                    .build();
        }
        
        List<ChartDataDTO.DataPoint> dataPoints = new ArrayList<>();
        DateTimeFormatter formatter = getFormatterForPeriod(period);
        
        double previousValue = readings.get(0).getGallons();
        double totalUsage = 0;
        
        for (int i = 0; i < readings.size(); i++) {
            WaterReading reading = readings.get(i);
            double usage = i > 0 ? reading.getGallons() - readings.get(i-1).getGallons() : 0;
            totalUsage += usage;
            
            dataPoints.add(ChartDataDTO.DataPoint.builder()
                    .timestamp(reading.getTimestamp().format(formatter))
                    .value(reading.getGallons())
                    .usage(usage)
                    .label(formatLabel(reading.getTimestamp(), period))
                    .build());
        }
        
        return ChartDataDTO.builder()
                .dataPoints(aggregateDataPoints(dataPoints, period))
                .period(period)
                .totalUsage(totalUsage)
                .build();
    }
    
    private List<ChartDataDTO.DataPoint> aggregateDataPoints(List<ChartDataDTO.DataPoint> dataPoints, String period) {
        if (period.equalsIgnoreCase("day") || dataPoints.size() <= 100) {
            return dataPoints;
        }
        
        // Aggregate data points for better visualization
        Map<String, List<ChartDataDTO.DataPoint>> grouped = new LinkedHashMap<>();
        
        for (ChartDataDTO.DataPoint dp : dataPoints) {
            String key = dp.getLabel();
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(dp);
        }
        
        return grouped.entrySet().stream()
                .map(entry -> {
                    List<ChartDataDTO.DataPoint> points = entry.getValue();
                    double avgValue = points.stream().mapToDouble(ChartDataDTO.DataPoint::getValue).average().orElse(0);
                    double totalUsage = points.stream().mapToDouble(ChartDataDTO.DataPoint::getUsage).sum();
                    
                    return ChartDataDTO.DataPoint.builder()
                            .timestamp(points.get(points.size() - 1).getTimestamp())
                            .value(avgValue)
                            .usage(totalUsage)
                            .label(entry.getKey())
                            .build();
                })
                .collect(Collectors.toList());
    }
    
    private DateTimeFormatter getFormatterForPeriod(String period) {
        return switch (period.toLowerCase()) {
            case "day" -> DateTimeFormatter.ofPattern("HH:mm");
            case "week" -> DateTimeFormatter.ofPattern("EEE HH:mm");
            case "month" -> DateTimeFormatter.ofPattern("MMM dd");
            case "ytd" -> DateTimeFormatter.ofPattern("MMM dd");
            default -> DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        };
    }
    
    private String formatLabel(ZonedDateTime timestamp, String period) {
        return switch (period.toLowerCase()) {
            case "day" -> timestamp.format(DateTimeFormatter.ofPattern("HH:mm"));
            case "week" -> timestamp.format(DateTimeFormatter.ofPattern("EEE"));
            case "month" -> timestamp.format(DateTimeFormatter.ofPattern("MMM dd"));
            case "ytd" -> timestamp.format(DateTimeFormatter.ofPattern("MMM"));
            default -> timestamp.format(DateTimeFormatter.ofPattern("MMM dd HH:mm"));
        };
    }
    
    private Map<LocalDate, Double> calculateDailyUsage(List<WaterReading> readings) {
        Map<LocalDate, Double> dailyUsage = new LinkedHashMap<>();
        
        for (int i = 1; i < readings.size(); i++) {
            WaterReading current = readings.get(i);
            WaterReading previous = readings.get(i - 1);
            LocalDate date = current.getTimestamp().toLocalDate();
            
            double usage = current.getGallons() - previous.getGallons();
            dailyUsage.merge(date, usage, Double::sum);
        }
        
        return dailyUsage;
    }
    
    private List<WaterReading> generateMockData() {
        log.info("Generating mock data for demonstration");
        List<WaterReading> mockData = new ArrayList<>();
        ZonedDateTime now = ZonedDateTime.now();
        Random random = new Random();
        
        double baseReading = 490000;
        for (int days = 365; days >= 0; days--) {
            for (int hours = 0; hours < 24; hours += 3) {
                ZonedDateTime timestamp = now.minusDays(days).withHour(hours).withMinute(0).withSecond(0);
                double dailyUsage = 100 + random.nextDouble() * 200; // 100-300 gallons per day
                baseReading += dailyUsage / 8; // Divide by 8 since we have 8 readings per day
                
                WaterReading reading = new WaterReading();
                reading.setTimestamp(timestamp);
                reading.setMeterReading((long)(baseReading / 10));
                reading.setGallons(baseReading);
                reading.setRawData("MOCK_DATA");
                mockData.add(reading);
            }
        }
        
        return mockData;
    }
}