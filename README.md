# Water Usage Monitor

A comprehensive water usage monitoring system built with Spring Boot that tracks water consumption from Neptune water meters, provides visualizations, and sends automated alerts.

## Features

- **Real-time Dashboard**: Interactive web interface with usage charts and statistics
- **Multiple Time Views**: Day, Week, Month, and Year-to-Date data visualization
- **Email Alerts**:
  - Daily usage summaries at 9:00 PM
  - Abnormal usage detection (>50 gallons in 30 minutes)
  - Meter failure notifications
- **Responsive Design**: Works on desktop, tablet, and mobile devices

## Hardware Requirements

1. **Neptune Water Meter** with wireless transmission capability
2. **SDR Radio Receiver**: Nooelec NESDR Mini USB RTL-SDR & ADS-B Receiver Set (RTL2832U & R820T Tuner)

## Software Requirements

1. **Operating System**: Debian Linux 12 (or compatible)
2. **Java**: JDK 17 or higher
3. **Maven**: 3.6 or higher
4. **SDR Software**:
   - rtlsdr - Driver for SDR radio ([https://gitea.osmocom.org/sdr/rtl-sdr.git](https://gitea.osmocom.org/sdr/rtl-sdr.git))
   - rtlamr - Meter reading software ([https://github.com/bemasher/rtlamr](https://github.com/bemasher/rtlamr))

## Data Collection Setup

### 1. Install SDR Software

Follow the installation guides for rtlsdr and rtlamr based on your system configuration.

### 2. Configure Data Collection Script

Create a shell script to continuously collect meter readings:

```bash
#!/bin/bash
while true
do
  ~/go/bin/rtlamr -msgtype=all -format=csv -centerfreq=914000155 -filterid=30904705 -single=true >>~/bin/monitor.out
  sleep 60
done
```

**Important Configuration**:
- `centerfreq`: Adjust to your meter's transmission frequency
- `filterid`: Replace with your Neptune meter's serial number
- Output file: `~/bin/monitor.out` (default location)

### 3. CSV Data Format

The meter readings are stored in CSV format:
```
2024-08-11T15:40:18.596641644-04:00,0,0,30904705,13,0x0,0x0,44465,0xa0b9
```
- Column 8 (`44465`): Water meter reading in 10-gallon units (multiply by 10 for actual gallons)

## Application Installation

### 1. Clone the Repository

```bash
git clone https://github.com/Jeff31UK/WaterUsageCalculator.git
cd WaterUsageCalculator
```

### 2. Configure the Application

Copy the template configuration file:
```bash
cp src/main/resources/application.properties.default src/main/resources/application.properties
```

Edit `application.properties` to add your email credentials:
```properties
spring.mail.username=your-email@gmail.com
spring.mail.password=your-app-specific-password
email.recipient=recipient@example.com
```

**Note**: Use Gmail app-specific passwords for security. Enable 2FA and generate an app password at [https://myaccount.google.com/apppasswords](https://myaccount.google.com/apppasswords)

### 3. Build and Run

```bash
# Build the application
mvn clean package

# Run the application
./start.sh
# or
mvn spring-boot:run
```

The application will be available at [http://localhost:8080](http://localhost:8080)

## Application Architecture

### Backend (Spring Boot)
- **REST API**: Provides endpoints for data retrieval and email sending
- **Scheduled Tasks**: Automated monitoring and daily summaries
- **Email Service**: Sends alerts using JavaMail/SMTP
- **Data Processing**: Reads and analyzes CSV meter data

### Frontend (Vanilla JavaScript)
- **Chart.js**: Interactive data visualizations
- **Responsive Design**: Mobile-friendly interface
- **Real-time Updates**: Auto-refreshes every 5 minutes

## API Endpoints

- `GET /api/water/readings` - All water readings
- `GET /api/water/chart/{period}` - Chart data (day/week/month/ytd)
- `GET /api/water/stats/{period}` - Usage statistics
- `POST /api/water/email/{period}` - Send email summary
- `GET /api/water/monitoring/status` - System status
- `GET /api/water/health` - Health check

## Email Notifications

### Daily Summary (9:00 PM)
- Hourly usage breakdown
- Daily totals and averages
- Comparison with previous day

### Abnormal Usage Alert
- Triggered when >50 gallons used in 30 minutes
- Sent maximum once per day
- Includes usage details and timestamp

### Meter Failure Alert
- Triggered when no readings for 30+ minutes
- Sent once until readings resume

## Environment Variables

Instead of storing credentials in `application.properties`, you can use environment variables:

```bash
export GMAIL_USERNAME=your-email@gmail.com
export GMAIL_APP_PASSWORD=your-app-password
export EMAIL_RECIPIENT=recipient@example.com
```

## Project Structure

```
├── pom.xml                           # Maven configuration
├── start.sh                          # Startup script
├── src/
│   └── main/
│       ├── java/com/watermonitor/
│       │   ├── controller/          # REST endpoints
│       │   ├── service/             # Business logic
│       │   ├── model/               # Data models
│       │   └── dto/                 # Data transfer objects
│       └── resources/
│           ├── application.properties
│           ├── templates/           # HTML templates
│           └── static/              # CSS and JavaScript
└── README.md
```

## Troubleshooting

### No Data Showing
- Verify `~/bin/monitor.out` exists and is being updated
- Check file format matches expected CSV structure
- Application generates mock data if file is missing

### Email Not Sending
- Verify Gmail app password is correct
- Check SMTP settings in application.properties
- Review logs at `/tmp/spring.log`

### Meter Reading Issues
- Ensure rtlamr is running and writing to monitor.out
- Check SDR radio connection
- Verify frequency and meter ID settings

## Contributing

Feel free to submit issues and pull requests.

## License

This project is open source and available under the MIT License.

## Author

Jeff31UK

## Acknowledgments

- Based on original WaterUsageCalculator.java implementation
- Uses rtlamr for meter reading ([https://github.com/bemasher/rtlamr](https://github.com/bemasher/rtlamr))
- Built with Spring Boot and Chart.js