package com.watermonitor.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.ZonedDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WaterReading {
    private ZonedDateTime timestamp;
    private long meterReading; // in 10-gallon units
    private double gallons; // actual gallons
    private String rawData;
    
    public static WaterReading fromCsvLine(String line) {
        String[] parts = line.split(",");
        WaterReading reading = new WaterReading();
        reading.timestamp = ZonedDateTime.parse(parts[0]);
        reading.meterReading = Long.parseLong(parts[7]);
        reading.gallons = reading.meterReading * 10.0;
        reading.rawData = line;
        return reading;
    }
}