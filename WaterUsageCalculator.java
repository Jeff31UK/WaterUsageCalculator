import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.TreeMap;

import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Properties;

public class WaterUsageCalculator {

    private static final DateTimeFormatter formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    public static void main(String[] args) { 
        if (args.length < 2) {
            System.out.println("Usage: java WaterUsageCalculator <csv file> <email username> <email password>\r\n\r\n");
        }
        else {    
            calculateWaterUsage(args[0], args[1], args[2]);
        }
    }

    public static void calculateWaterUsage(String filePath, String emailUserName, String emailPassword) {

        while (true) {
            Map<LocalDateTime, Integer> hourlyUsage = new TreeMap<>();
            Map<LocalDateTime, Integer> dailyUsage = new TreeMap<>();
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime oneDayAgo = now.minusDays(1);
            LocalDateTime fiveDaysAgo = now.minusDays(5);
            boolean triggerAbnormalFl = false;
            String output = "";

            try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] values = line.split(",");
                    LocalDateTime timestamp = LocalDateTime.parse(values[0], formatter);
                    int gallonsUsed = Integer.parseInt(values[7]) * 10;

                    if (timestamp.isAfter(fiveDaysAgo)) {
                        // Round down to the start of the hour
                        LocalDateTime roundedHour = timestamp.truncatedTo(ChronoUnit.HOURS);
                        LocalDateTime roundedDay = timestamp.truncatedTo(ChronoUnit.DAYS);

                        // Add the usage to the corresponding hour and day
                        hourlyUsage.put(roundedHour, gallonsUsed);
                        dailyUsage.put(roundedDay, gallonsUsed);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            // Calculate usage for the last day (24 hours)
            int firstEntry = 0;
            int lastEntry = 0;
            output += "Hourly usage for the last 24 hours:\r\n";
            for (Map.Entry<LocalDateTime, Integer> entry : hourlyUsage.entrySet()) {
                if (entry.getKey().isAfter(oneDayAgo)) {
                    if (firstEntry == 0)
                        firstEntry = entry.getValue();

                    output += entry.getKey() + " -> " + String.format("%5d", entry.getValue() - firstEntry) + " total gallons -> " + String.format("%5d", ((entry.getValue() - firstEntry) - lastEntry)) + " gallons / hour\r\n";

                    // If usage per hour is more than this threshold send 'water abnormal usage' email...
                    if (((entry.getValue() - firstEntry) - lastEntry) > 100) {
                        triggerAbnormalFl = true;
                    }

                    lastEntry = (entry.getValue() - firstEntry);
                }
            }

            output += "\r\n";

            // Calculate usage for the last 5 days
            firstEntry = 0; 
            lastEntry = 0;
            output += "Daily usage for the last 5 days:r\n";
            for (Map.Entry<LocalDateTime, Integer> entry : dailyUsage.entrySet()) {
                if (entry.getKey().isAfter(fiveDaysAgo)) {
                    if (firstEntry == 0)
                        firstEntry = entry.getValue();

                    output += entry.getKey().toLocalDate() + " -> " + String.format("%5d", entry.getValue() - firstEntry) + " total gallons -> " + String.format("%5d", ((entry.getValue() - firstEntry) - lastEntry)) + " gallons / day\r\n";
                    lastEntry = (entry.getValue() - firstEntry);
                }
            }

            System.out.println(output);

            if (triggerAbnormalFl) {
                output += "***** Warning!! Water usage is abnormal!!\r\n";

                sendEmail(emailUserName, emailPassword, "Water usage warning (abnormal)", "Warning: Water usage is abnormal! (> 50 gal per hour)\r\n\r\n" + output);
            }

            if (LocalDateTime.now().getHour() == 21) // 9pm
            {
                sendEmail(emailUserName, emailPassword, "Water usage Daily Digest", "Daily Digest below:\r\n\r\n" + output);
            }

            System.out.println("Sleep for 1 hour...");
            try {
                Thread.sleep(3600000);
            } catch (Exception e) { }
        } // Wend        
    }

    public static void sendEmail(String emailUserName, String emailPassword, String subject, String body) {

        Properties prop = new Properties();
        prop.put("mail.smtp.host", "smtp.gmail.com");
        prop.put("mail.smtp.port", "587");
        prop.put("mail.smtp.auth", "true");
        prop.put("mail.smtp.starttls.enable", "true"); //TLS
        
        Session session = Session.getInstance(prop,
                new javax.mail.Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(emailUserName, emailPassword);
                    }
                });
    
        try {
    
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress("from@gmail.com"));
            message.setRecipients(
                    Message.RecipientType.TO,
                    InternetAddress.parse("Jeff31UK@Hotmail.com")
            );
            message.setSubject(subject);
            message.setText(body);
    
            Transport.send(message);
    
            System.out.println("Email Sent.");
    
        } catch (MessagingException e) {
            e.printStackTrace();
        }
    }    
}

