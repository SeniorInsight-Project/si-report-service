package com.seniorinsight.report.adapters.in.mensageria;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.seniorinsight.report.application.projecao.AtualizarProjecoes;
import com.seniorinsight.report.domain.projecao.OcorrenciaSos;
import com.seniorinsight.report.domain.projecao.ScoreIdoso;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consome a fila {@code report.q} com ack manual.
 *
 * <p>O id do evento é o {@code message_id} (= id da linha na outbox de quem publicou) e o tipo vem em {@code type}
 * (ou, na falta, na routing key). Eventos repetidos são confirmados sem efeito. Mensagens que não puderam ser
 * processadas (sem id, payload inválido, erro no banco) recebem nack sem requeue e vão para {@code report.q.dlq}.
 */
@Component
class ConsumidorEventos {

    private static final Logger log = LoggerFactory.getLogger(ConsumidorEventos.class);

    private final AtualizarProjecoes atualizarProjecoes;
    private final ObjectMapper objectMapper;

    ConsumidorEventos(AtualizarProjecoes atualizarProjecoes, ObjectMapper objectMapper) {
        this.atualizarProjecoes = atualizarProjecoes;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = MensageriaConfig.FILA, ackMode = "MANUAL")
    void receber(Message mensagem, Channel canal) throws IOException {
        MessageProperties propriedades = mensagem.getMessageProperties();
        long tag = propriedades.getDeliveryTag();
        try {
            processar(mensagem);
            canal.basicAck(tag, false);
        } catch (Exception e) {
            log.error("report.q: mensagem {} ({}) enviada para a dead-letter: {}",
                    propriedades.getMessageId(), tipoEvento(propriedades), e.getMessage(), e);
            canal.basicNack(tag, false, false);
        }
    }

    private void processar(Message mensagem) throws IOException {
        MessageProperties propriedades = mensagem.getMessageProperties();
        if (propriedades.getMessageId() == null) {
            throw new IllegalArgumentException("mensagem sem message_id");
        }
        UUID eventoId = UUID.fromString(propriedades.getMessageId());
        String tipo = tipoEvento(propriedades);

        boolean aplicado;
        if (tipo.startsWith("score.")) {
            ScorePayload payload = objectMapper.readValue(mensagem.getBody(), ScorePayload.class);
            aplicado = atualizarProjecoes.aplicarScore(eventoId, tipo,
                    new ScoreIdoso(payload.idosoId(), payload.score(), payload.calculadoEm()));
        } else if (tipo.startsWith("sos.")) {
            SosPayload payload = objectMapper.readValue(mensagem.getBody(), SosPayload.class);
            aplicado = atualizarProjecoes.aplicarSos(eventoId, tipo,
                    new OcorrenciaSos(payload.sosId(), payload.idosoId(), payload.status(), payload.ocorridoEm()));
        } else {
            log.debug("report.q: evento {} do tipo {} ignorado", eventoId, tipo);
            return;
        }
        if (!aplicado) {
            log.debug("report.q: evento {} ({}) já processado, ignorado", eventoId, tipo);
        }
    }

    private static String tipoEvento(MessageProperties propriedades) {
        return propriedades.getType() != null ? propriedades.getType() : propriedades.getReceivedRoutingKey();
    }

    record ScorePayload(UUID idosoId, BigDecimal score, Instant calculadoEm) {
    }

    record SosPayload(UUID sosId, UUID idosoId, String status, Instant ocorridoEm) {
    }
}
