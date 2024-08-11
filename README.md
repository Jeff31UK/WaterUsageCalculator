
I use this program as described below to monitor my water usage. My water meter is a NEPTUNE brand which wirelessly transmits its usage data.

Hardware:
1. "Nooelec NESDR Mini USB RTL-SDR & ADS-B Receiver Set, RTL2832U & R820T Tuner, MCX Input. Low-Cost Software Defined Radio Compatible with Many SDR Software Packages. R820T Tuner & ESD-Safe Antenna Input" (Amazon)

Software:
1. Debian Linux 12
2. rtlsdr ('driver' to talk to the SDR radio)
3. rtlamr (used to read from a frequency and output to a CSV file)
4. This Java program to parse CSV file and output usage
5. Optional: Google email account to send notifications


RTLSDR: (https://gitea.osmocom.org/sdr/rtl-sdr.git)
RTLAMR: (https://github.com/bemasher/rtlamr@latest)

I won't detail compiling and installing those as it'll be dependant on your setup.

Once you get RTLSDR & RTLAMR working, I run the following shell script to append my usage to my CSV file:

while true
do
  ~/go/bin/rtlamr  -msgtype=all -format=csv -centerfreq=914000155 -filterid=30904705 -single=true >>monitor.out
  sleep 60
done

You'll need to modify centerfreq parameter to find the frequency your meter is transmitting on
You'll need to modify filterid to correspond to your NEPTUNE water meter transmitter serial #

With this running, you'll get a CSV file like this:

2024-08-11T15:40:18.596641644-04:00,0,0,30904705,13,0x0,0x0,44465,0xa0b9
2024-08-11T15:41:21.587338171-04:00,0,0,30904705,13,0x0,0x0,44465,0xa0b9
2024-08-11T15:42:38.525901081-04:00,0,0,30904705,13,0x0,0x0,44465,0xa0b9
2024-08-11T15:43:41.660479083-04:00,0,0,30904705,13,0x0,0x0,44465,0xa0b9
2024-08-11T15:44:44.288284991-04:00,0,0,30904705,13,0x0,0x0,44465,0xa0b9
(the water meter reading is the column 44465 * 10 gallons)

Now for the Java program.

To compile and run it you need the following libraries:
activation-1.1.1.jar
jakarta-activation-api-1.2.1.jar
javax.mail.jar

.. Once compiled you run like this:
export CLASSPATH=javax.mail.jar:.:activation-1.1.1.jar:
java WaterUsageCalculator <csv file> <smtp username> <smtp password>

The program is pretty self explanatory, please feel free to change as needed.






