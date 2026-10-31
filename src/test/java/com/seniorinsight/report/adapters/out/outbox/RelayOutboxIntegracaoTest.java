package com.seniorinsight.report.adapters.out.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seniorinsight.report.TesteIntegracao;
import com.seniorinsight.report.application.porta.saida.PublicadorEventos;
import com.seniorinsight.report.domain.evento.EventoDominio;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/** Caminho completo: caso de uso grava na outbox, o job agendado publica no RabbitMQ e marca a linha. */
@TesteIntegracao
class RelayOutboxIntegracaoTest {

    @Autowired
    PublicadorEventos publicador;

    @Autowired
    OutboxEventoRepository repositorio;

    @Autowired
    TransactionTemplate transacao;

    @Autowired
    AmqpAdmin amqpAdmin;

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void publicaNaExchangeSiEventsComRoutingKeyIgualAoTipoDoEventoEMarcaPublicado() throws Exception {
        Queue fila = amqpAdmin.declareQueue();
        amqpAdmin.declareBinding(BindingBuilder.bind(fila)
                .to(new TopicExchange(OutboxConfig.EXCHANGE_EVENTOS))
                .with("report.ready"));
        UUID relatorioId = UUID.randomUUID();

        transacao.executeWithoutResult(status -> publicador.publicar(new RelatorioPronto(relatorioId, "semanal")));

        Message mensagem = rabbitTemplate.receive(fila.getName(), Duration.ofSeconds(15).toMillis());
        assertThat(mensagem).as("mensagem publicada pelo relay").isNotNull();
        MessageProperties propriedades = mensagem.getMessageProperties();
        assertThat(propriedades.getReceivedExchange()).isEqualTo("si.events");
        assertThat(propriedades.getReceivedRoutingKey()).isEqualTo("report.ready");
        assertThat(propriedades.getType()).isEqualTo("report.ready");
        assertThat(propriedades.getContentType()).isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
        assertThat(propriedades.getAppId()).isEqualTo("report-service");
        assertThat(propriedades.<String>getHeader("tipo_agregado")).isEqualTo("relatorio");
        assertThat(propriedades.<String>getHeader("agregado_id")).isEqualTo(relatorioId.toString());
        assertThat(objectMapper.readTree(mensagem.getBody()))
                .isEqualTo(objectMapper.createObjectNode()
                        .put("relatorioId", relatorioId.toString()).put("tipo", "semanal"));

        UUID eventoId = UUID.fromString(propriedades.getMessageId());
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            OutboxEvento evento = repositorio.findById(eventoId).orElseThrow();
            assertThat(evento.getAgregadoId()).isEqualTo(relatorioId);
            assertThat(evento.getPublicadoEm()).isNotNull();
            assertThat(evento.getTentativas()).isZero();
        });
    }

    @Test
    void eventoNaoEhGravadoQuandoATransacaoDoCasoDeUsoEhDesfeita() {
        UUID relatorioId = UUID.randomUUID();

        transacao.executeWithoutResult(status -> {
            publicador.publicar(new RelatorioPronto(relatorioId, "semanal"));
            status.setRollbackOnly();
        });

        assertThat(repositorio.findAll()).noneMatch(evento -> evento.getAgregadoId().equals(relatorioId));
    }

    @Test
    void publicarExigeTransacaoAtiva() {
        assertThatThrownBy(() -> publicador.publicar(new RelatorioPronto(UUID.randomUUID(), "semanal")))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    record RelatorioPronto(UUID relatorioId, String tipo) implements EventoDominio {

        @Override
        public String tipoAgregado() {
            return "relatorio";
        }

        @Override
        public UUID agregadoId() {
            return relatorioId;
        }

        @Override
        public String tipoEvento() {
            return "report.ready";
        }
    }
}
