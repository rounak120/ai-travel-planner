package com.aibot.travelplanner.weather;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;

@Configuration
public class WeatherConfiguration {
    @Bean
    public Clock weatherClock() {
        return Clock.systemUTC();
    }
}
