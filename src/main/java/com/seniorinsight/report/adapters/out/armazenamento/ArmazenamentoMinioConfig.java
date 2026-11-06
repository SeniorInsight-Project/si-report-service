package com.seniorinsight.report.adapters.out.armazenamento;

import com.seniorinsight.report.application.porta.saida.ArmazenamentoObjetos;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Liga o adaptador MinIO quando {@code report.armazenamento.provedor=minio} (padrão local). Em produção o provedor
 * será o Cloud Storage, com outro adaptador da mesma porta {@link ArmazenamentoObjetos}.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "report.armazenamento.provedor", havingValue = "minio")
public class ArmazenamentoMinioConfig {

    @Bean
    ArmazenamentoObjetos armazenamentoObjetos(
            @Value("${report.armazenamento.bucket}") String bucket,
            @Value("${report.armazenamento.minio.endpoint}") String endpoint,
            @Value("${report.armazenamento.minio.endpoint-publico}") String endpointPublico,
            @Value("${report.armazenamento.minio.regiao}") String regiao,
            @Value("${report.armazenamento.minio.access-key}") String accessKey,
            @Value("${report.armazenamento.minio.secret-key}") String secretKey) {
        return new ArmazenamentoMinio(
                cliente(endpoint, regiao, accessKey, secretKey),
                cliente(endpointPublico, regiao, accessKey, secretKey),
                bucket);
    }

    private static MinioClient cliente(String endpoint, String regiao, String accessKey, String secretKey) {
        // Com a região fixa o cliente não consulta o servidor para descobri-la (necessário para assinar offline).
        return MinioClient.builder().endpoint(endpoint).region(regiao).credentials(accessKey, secretKey).build();
    }
}
