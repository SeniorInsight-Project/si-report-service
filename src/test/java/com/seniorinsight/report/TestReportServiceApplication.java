package com.seniorinsight.report;

import org.springframework.boot.SpringApplication;

/** Sobe o serviço localmente com PostgreSQL, RabbitMQ e MinIO do Testcontainers: {@code mvn spring-boot:test-run}. */
public class TestReportServiceApplication {

    public static void main(String[] args) {
        SpringApplication.from(ReportServiceApplication::main).with(ContainersConfig.class).run(args);
    }
}
