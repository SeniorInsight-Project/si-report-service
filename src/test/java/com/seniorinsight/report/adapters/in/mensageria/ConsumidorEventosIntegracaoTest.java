package com.seniorinsight.report.adapters.in.mensageria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import com.seniorinsight.report.TesteIntegracao;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Eventos publicados em si.events chegam à report.q e atualizam as projeções uma única vez. */
@TesteIntegracao
class ConsumidorEventosIntegracaoTest {

    private static final Duration ESPERA = Duration.ofSeconds(15);

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void eventoDeScoreAtualizaAProjecao() {
        UUID idosoId = UUID.randomUUID();

        publicar(UUID.randomUUID(), "score.updated", """
                {"idosoId": "%s", "score": 72.5, "calculadoEm": "2026-10-30T12:00:00Z"}
                """.formatted(idosoId));

        await().atMost(ESPERA).untilAsserted(() -> assertThat(jdbc.queryForObject(
                "SELECT score FROM projecao_score WHERE idoso_id = ?", BigDecimal.class, idosoId))
                .isEqualByComparingTo("72.5"));
    }

    @Test
    void eventoRepetidoNaoEhAplicadoDuasVezes() {
        UUID eventoId = UUID.randomUUID();
        UUID sosId = UUID.randomUUID();
        String payload = """
                {"sosId": "%s", "idosoId": "%s", "status": "ABERTO", "ocorridoEm": "2026-10-30T12:00:00Z"}
                """.formatted(sosId, UUID.randomUUID());

        publicar(eventoId, "sos.triggered", payload);
        await().atMost(ESPERA).until(() -> statusSos(sosId) != null);

        // Muda o estado por fora: se a repetição fosse aplicada, o status voltaria a ABERTO.
        jdbc.update("UPDATE projecao_sos SET status = 'ENCERRADO' WHERE sos_id = ?", sosId);
        publicar(eventoId, "sos.triggered", payload);
        UUID marcador = UUID.randomUUID();
        publicar(marcador, "sos.triggered", """
                {"sosId": "%s", "idosoId": "%s", "status": "ABERTO", "ocorridoEm": "2026-10-30T12:00:00Z"}
                """.formatted(UUID.randomUUID(), UUID.randomUUID()));
        await().atMost(ESPERA).until(() -> processado(marcador));

        assertThat(statusSos(sosId)).isEqualTo("ENCERRADO");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM evento_processado WHERE evento_id = ?", Integer.class, eventoId)).isEqualTo(1);
    }

    @Test
    void mensagemInvalidaVaiParaADeadLetter() {
        UUID eventoId = UUID.randomUUID();

        publicar(eventoId, "score.updated", "{\"idosoId\": \"nao-eh-uuid\"}");

        Message morta = rabbitTemplate.receive(MensageriaConfig.FILA_DEAD_LETTER, ESPERA.toMillis());
        assertThat(morta).as("mensagem na report.q.dlq").isNotNull();
        assertThat(morta.getMessageProperties().getMessageId()).isEqualTo(eventoId.toString());
        assertThat(processado(eventoId)).isFalse();
    }

    private void publicar(UUID eventoId, String tipo, String json) {
        Message mensagem = MessageBuilder.withBody(json.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setMessageId(eventoId.toString())
                .setType(tipo)
                .build();
        rabbitTemplate.send(MensageriaConfig.EXCHANGE_EVENTOS, tipo, mensagem);
    }

    private String statusSos(UUID sosId) {
        return jdbc.queryForList("SELECT status FROM projecao_sos WHERE sos_id = ?", String.class, sosId)
                .stream().findFirst().orElse(null);
    }

    private boolean processado(UUID eventoId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM evento_processado WHERE evento_id = ?", Integer.class, eventoId) > 0;
    }
}
