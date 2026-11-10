package com.seniorinsight.report.adapters.in.mensageria;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.seniorinsight.report.application.exclusao.RegistrarConfirmacaoExclusao;
import com.seniorinsight.report.application.projecao.AtualizarProjecoes;
import com.seniorinsight.report.domain.exclusao.TipoTitular;
import com.seniorinsight.report.domain.projecao.AssinaturaFamiliar;
import com.seniorinsight.report.domain.projecao.OcorrenciaSos;
import com.seniorinsight.report.domain.projecao.PerfilTitular;
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
    private final RegistrarConfirmacaoExclusao registrarConfirmacaoExclusao;
    private final ObjectMapper objectMapper;

    ConsumidorEventos(AtualizarProjecoes atualizarProjecoes, RegistrarConfirmacaoExclusao registrarConfirmacaoExclusao,
                      ObjectMapper objectMapper) {
        this.atualizarProjecoes = atualizarProjecoes;
        this.registrarConfirmacaoExclusao = registrarConfirmacaoExclusao;
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
        } else if (tipo.equals("user.created")) {
            PerfilPayload perfil = dadosDoEnvelope(mensagem, PerfilPayload.class);
            aplicado = atualizarProjecoes.aplicarPerfil(eventoId, tipo, new PerfilTitular(perfil.perfilId(),
                    perfil.keycloakSub(), TipoTitular.valueOf(perfil.tipo()), perfil.nome(), perfil.email()));
        } else if (tipo.equals("subscription.changed")) {
            AssinaturaPayload assinatura = dadosDoEnvelope(mensagem, AssinaturaPayload.class);
            aplicado = atualizarProjecoes.aplicarAssinatura(eventoId, tipo,
                    new AssinaturaFamiliar(assinatura.familiarSub(), assinatura.plano(), assinatura.ativa()));
        } else if (tipo.equals("lgpd.erasure.completed")) {
            ConfirmacaoExclusaoPayload confirmacao = dadosDoEnvelope(mensagem, ConfirmacaoExclusaoPayload.class);
            aplicado = registrarConfirmacaoExclusao.executar(eventoId, tipo, confirmacao.protocolo(),
                    confirmacao.servico());
        } else {
            log.debug("report.q: evento {} do tipo {} ignorado", eventoId, tipo);
            return;
        }
        if (!aplicado) {
            log.debug("report.q: evento {} ({}) já processado, ignorado", eventoId, tipo);
        }
    }

    /** Os eventos dos contratos novos chegam como envelope CloudEvents; o que interessa está em {@code data}. */
    private <T> T dadosDoEnvelope(Message mensagem, Class<T> tipo) throws IOException {
        JsonNode data = objectMapper.readTree(mensagem.getBody()).path("data");
        if (!data.isObject()) {
            throw new IllegalArgumentException("envelope sem data");
        }
        return objectMapper.treeToValue(data, tipo);
    }

    private static String tipoEvento(MessageProperties propriedades) {
        return propriedades.getType() != null ? propriedades.getType() : propriedades.getReceivedRoutingKey();
    }

    record ScorePayload(UUID idosoId, BigDecimal score, Instant calculadoEm) {
    }

    record SosPayload(UUID sosId, UUID idosoId, String status, Instant ocorridoEm) {
    }

    record PerfilPayload(UUID perfilId, String keycloakSub, String tipo, String nome, String email) {
    }

    record AssinaturaPayload(String familiarSub, String plano, boolean ativa) {
    }

    record ConfirmacaoExclusaoPayload(UUID protocolo, String servico) {
    }
}
