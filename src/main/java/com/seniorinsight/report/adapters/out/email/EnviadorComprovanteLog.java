package com.seniorinsight.report.adapters.out.email;

import java.time.Instant;
import java.util.UUID;

import com.seniorinsight.report.application.porta.saida.EnviadorComprovante;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Sem SMTP configurado: registra que o comprovante seria enviado, sem gravar o endereço no log. */
class EnviadorComprovanteLog implements EnviadorComprovante {

    private static final Logger log = LoggerFactory.getLogger(EnviadorComprovanteLog.class);

    @Override
    public void enviar(String email, UUID protocolo, Instant solicitadoEm, Instant concluidoEm) {
        log.warn("Comprovante da exclusão {} não enviado: SMTP não configurado (SPRING_MAIL_HOST)", protocolo);
    }
}
