package com.srmecotech.plantride;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
@EnableScheduling
public class PlantRideApplication {

    public static void main(String[] args) {
        // The plant runs on IST; keep the JVM on the same zone as the DB sessions.
        TimeZone.setDefault(TimeZone.getTimeZone(System.getProperty("plantride.timezone", "Asia/Kolkata")));
        SpringApplication.run(PlantRideApplication.class, args);
    }
}
