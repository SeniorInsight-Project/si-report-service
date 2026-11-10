package com.seniorinsight.report.adapters.out.email;

import com.seniorinsight.report.application.porta.saida.EnviadorComprovante;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Envio por SMTP quando {@code spring.mail.host} (env {@code SPRING_MAIL_HOST}) está configurado; sem ele, o
 * comprovante só é registrado no log (ambiente local).
 */
@Configuration(proxyBeanMethods = false)
class EnviadorComprovanteConfig {

    @Bean
    @ConditionalOnProperty("spring.mail.host")
    EnviadorComprovante enviadorComprovanteSmtp(JavaMailSender mailSender,
                                                @Value("${report.exclusao.remetente}") String remetente) {
        return new EnviadorComprovanteSmtp(mailSender, remetente);
    }

    @Bean
    @ConditionalOnMissingBean(EnviadorComprovante.class)
    EnviadorComprovante enviadorComprovanteLog() {
        return new EnviadorComprovanteLog();
    }
}
