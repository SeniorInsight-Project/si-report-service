package com.seniorinsight.report;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class RelogioConfig {

    @Bean
    Clock relogio() {
        return Clock.systemUTC();
    }
}
