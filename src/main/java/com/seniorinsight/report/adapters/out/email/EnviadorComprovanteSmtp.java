package com.seniorinsight.report.adapters.out.email;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

import com.seniorinsight.report.application.porta.saida.EnviadorComprovante;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class EnviadorComprovanteSmtp implements EnviadorComprovante {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.of("pt", "BR"))
            .withZone(ZoneId.of("America/Sao_Paulo"));

    private final JavaMailSender mailSender;
    private final String remetente;

    EnviadorComprovanteSmtp(JavaMailSender mailSender, String remetente) {
        this.mailSender = mailSender;
        this.remetente = remetente;
    }

    @Override
    public void enviar(String email, UUID protocolo, Instant solicitadoEm, Instant concluidoEm) {
        SimpleMailMessage mensagem = new SimpleMailMessage();
        mensagem.setFrom(remetente);
        mensagem.setTo(email);
        mensagem.setSubject("SeniorInsight: comprovante de exclusão de dados");
        mensagem.setText(corpo(protocolo, solicitadoEm, concluidoEm));
        mailSender.send(mensagem);
    }

    static String corpo(UUID protocolo, Instant solicitadoEm, Instant concluidoEm) {
        return """
                Olá,

                Confirmamos que a exclusão dos dados pessoais no SeniorInsight foi concluída, conforme a Lei Geral de \
                Proteção de Dados (LGPD).

                Protocolo: %s
                Pedido feito em: %s
                Exclusão concluída em: %s

                Guarde este e-mail como comprovante. Dados de telemetria anonimizados, que não identificam você, \
                podem ser mantidos conforme a política de retenção.

                Equipe SeniorInsight
                """.formatted(protocolo, DATA.format(solicitadoEm), DATA.format(concluidoEm));
    }
}
