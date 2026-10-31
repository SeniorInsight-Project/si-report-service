package com.seniorinsight.report;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * PostgreSQL e RabbitMQ em containers, ligados ao Spring via {@link ServiceConnection}; MinIO ligado pelas
 * propriedades {@code report.armazenamento.minio.*}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ContainersConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));
    }

    @Bean
    @ServiceConnection
    RabbitMQContainer rabbitmq() {
        return new RabbitMQContainer(DockerImageName.parse("rabbitmq:4.1-management-alpine"));
    }

    /** A imagem oficial (minio/minio) não é mais publicada; o pgsty/minio é um build comunitário do mesmo servidor. */
    @Bean
    MinIOContainer minio() {
        return new MinIOContainer(DockerImageName.parse("pgsty/minio:RELEASE.2026-08-04T00-00-00Z")
                .asCompatibleSubstituteFor("minio/minio"));
    }

    @Bean
    DynamicPropertyRegistrar propriedadesMinio(MinIOContainer minio) {
        return registro -> {
            registro.add("report.armazenamento.provedor", () -> "minio");
            registro.add("report.armazenamento.minio.endpoint", minio::getS3URL);
            registro.add("report.armazenamento.minio.endpoint-publico", minio::getS3URL);
            registro.add("report.armazenamento.minio.access-key", minio::getUserName);
            registro.add("report.armazenamento.minio.secret-key", minio::getPassword);
        };
    }
}
