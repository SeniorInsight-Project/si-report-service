package com.seniorinsight.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.seniorinsight.report.application.porta.saida.ArmazenamentoObjetos;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MinIOContainer;

/**
 * Pedido de exclusão de ponta a ponta, com PostgreSQL, RabbitMQ e MinIO reais: o perfil chega por {@code user.created},
 * o POST (com o {@code sub} do Keycloak em {@code titularId}) grava o pedido com o id do perfil, publica
 * {@code lgpd.erasure.requested} pelo outbox (id do perfil em {@code titularId}, {@code sub} em {@code titularSub}) e
 * apaga os arquivos; as confirmações dos
 * seis serviços chegam por {@code lgpd.erasure.completed} e a última dispara o comprovante.
 */
@TesteIntegracao
class ExclusaoIntegracaoTest {

    private static final Duration ESPERA = Duration.ofSeconds(20);
    private static final String BUCKET = "si-reports";
    private static final String[] SERVICOS = {
        "identity-service", "care-service", "billing-service", "insight-service", "notification-service",
        "telemetry-service"
    };

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    AmqpAdmin amqpAdmin;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    ArmazenamentoObjetos armazenamento;

    @Autowired
    MinIOContainer minio;

    @Autowired
    ComprovantesEnviados comprovantes;

    @Test
    void pedidoApagaOsDadosPublicaOEventoEOComprovanteSaiQuandoTodosConfirmam() throws Exception {
        UUID idosoId = UUID.randomUUID();
        UUID outroIdosoId = UUID.randomUUID();
        UUID subDoOutro = UUID.randomUUID();
        UUID subIdoso = UUID.randomUUID();
        String sub = subIdoso.toString();
        publicarUserCreated(idosoId, sub, "IDOSO", "maria@email.com");
        publicarUserCreated(outroIdosoId, subDoOutro.toString(), "IDOSO", "outra@email.com");
        await().atMost(ESPERA).until(() -> perfis(outroIdosoId) == 1 && perfis(idosoId) == 1);

        String pdf = "relatorios/" + idosoId + "/semanal.pdf";
        String pdfDoOutro = "relatorios/" + outroIdosoId + "/semanal.pdf";
        armazenamento.salvar(pdf, "pdf".getBytes(StandardCharsets.UTF_8), "application/pdf");
        armazenamento.salvar(pdfDoOutro, "pdf".getBytes(StandardCharsets.UTF_8), "application/pdf");
        semearRelatorio(idosoId, pdf);
        semearRelatorio(outroIdosoId, pdfDoOutro);
        jdbc.update("INSERT INTO projecao_score (idoso_id, score, calculado_em) VALUES (?, 80, now())", idosoId);

        Queue fila = amqpAdmin.declareQueue();
        amqpAdmin.declareBinding(BindingBuilder.bind(fila).to(new TopicExchange("si.events"))
                .with("lgpd.erasure.requested"));

        // titularId de outra pessoa: 403, sem registrar pedido nem apagar nada.
        solicitar(subDoOutro, sub, "idoso", "EXCLUIR").andExpect(status().isForbidden());
        assertThat(pedidosDoTitular(outroIdosoId)).isZero();
        assertThat(perfis(outroIdosoId)).isEqualTo(1);
        // Confirmação errada.
        solicitar(subIdoso, sub, "idoso", "excluir").andExpect(status().isBadRequest());

        String resposta = solicitar(subIdoso, sub, "idoso", "EXCLUIR")
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        UUID protocolo = UUID.fromString(objectMapper.readTree(resposta).get("protocolo").asText());
        assertThat(objectMapper.readTree(resposta).get("prazoDias").asInt()).isEqualTo(15);

        // Evento publicado pelo outbox, no formato do contrato.
        Message mensagem = rabbitTemplate.receive(fila.getName(), ESPERA.toMillis());
        assertThat(mensagem).as("lgpd.erasure.requested publicado").isNotNull();
        JsonNode envelope = objectMapper.readTree(mensagem.getBody());
        assertThat(contrato("lgpd.erasure.requested.v1.json").validate(envelope)).isEmpty();
        assertThat(envelope.get("subject").asText()).isEqualTo(protocolo.toString());
        assertThat(envelope.at("/data/protocolo").asText()).isEqualTo(protocolo.toString());
        assertThat(envelope.at("/data/titularId").asText()).isEqualTo(idosoId.toString());
        assertThat(envelope.at("/data/titularSub").asText()).isEqualTo(sub);
        assertThat(envelope.at("/data/tipoTitular").asText()).isEqualTo("IDOSO");

        // Dados do titular apagados; os do outro titular ficam.
        assertThat(contar("relatorio", idosoId)).isZero();
        assertThat(contar("projecao_score", idosoId)).isZero();
        assertThat(perfis(idosoId)).isZero();
        assertThat(existeNoArmazenamento(pdf)).isFalse();
        assertThat(contar("relatorio", outroIdosoId)).isEqualTo(1);
        assertThat(existeNoArmazenamento(pdfDoOutro)).isTrue();
        Map<String, Object> pedido = jdbc.queryForMap(
                "SELECT status, prazo_em - criado_em AS prazo FROM pedido_titular WHERE id = ?", protocolo);
        assertThat(pedido.get("status")).isEqualTo("EM_ANDAMENTO");
        assertThat(pedido.get("prazo").toString()).contains("15 days");

        // Já há um pedido em andamento (o perfil sumiu, então a repetição deixa de achar o titular).
        publicarUserCreated(idosoId, sub, "IDOSO", "maria@email.com");
        await().atMost(ESPERA).until(() -> perfis(idosoId) == 1);
        solicitar(subIdoso, sub, "idoso", "EXCLUIR").andExpect(status().isConflict());

        // Cinco confirmações: ainda sem comprovante. A sexta conclui o pedido.
        UUID ultimoEvento = null;
        for (int i = 0; i < SERVICOS.length - 1; i++) {
            publicarConfirmacao(UUID.randomUUID(), protocolo, SERVICOS[i]);
        }
        await().atMost(ESPERA).until(() -> confirmacoes(protocolo) == 5);
        assertThat(comprovantes.doProtocolo(protocolo)).isEmpty();

        ultimoEvento = UUID.randomUUID();
        publicarConfirmacao(ultimoEvento, protocolo, SERVICOS[5]);
        await().atMost(ESPERA).until(() -> comprovantes.doProtocolo(protocolo).size() == 1);

        assertThat(comprovantes.doProtocolo(protocolo).getFirst().email()).isEqualTo("maria@email.com");
        Map<String, Object> concluido = jdbc.queryForMap(
                "SELECT status, email_comprovante, concluido_em FROM pedido_titular WHERE id = ?", protocolo);
        assertThat(concluido.get("status")).isEqualTo("CONCLUIDO");
        assertThat(concluido.get("email_comprovante")).isNull();
        assertThat(concluido.get("concluido_em")).isNotNull();

        // Entrega repetida da última confirmação não reenvia o comprovante.
        publicarConfirmacao(ultimoEvento, protocolo, SERVICOS[5]);
        UUID marcador = UUID.randomUUID();
        publicarConfirmacao(marcador, UUID.randomUUID(), "identity-service");
        await().atMost(ESPERA).until(() -> processado(marcador));
        assertThat(comprovantes.doProtocolo(protocolo)).hasSize(1);
    }

    @Test
    void familiarComAssinaturaPremiumAtivaNaoApagaAConta() throws Exception {
        UUID familiarId = UUID.randomUUID();
        UUID subFamiliar = UUID.randomUUID();
        String sub = subFamiliar.toString();
        publicarUserCreated(familiarId, sub, "FAMILIAR", "ana@email.com");
        publicar(UUID.randomUUID(), "subscription.changed", Map.of(
                "familiarSub", sub, "plano", "PREMIUM", "ativa", true, "expiraEm", "2027-01-01T00:00:00Z",
                "emCarencia", false));
        await().atMost(ESPERA).until(() -> perfis(familiarId) == 1
                && contarAssinaturas(sub) == 1);

        solicitar(subFamiliar, sub, "familiar", "EXCLUIR").andExpect(status().isConflict());

        assertThat(perfis(familiarId)).isEqualTo(1);
        assertThat(pedidosDoTitular(familiarId)).isZero();
    }

    @Test
    void subSemPerfilResponde404() throws Exception {
        UUID semPerfil = UUID.randomUUID();

        solicitar(semPerfil, semPerfil.toString(), "idoso", "EXCLUIR").andExpect(status().isNotFound());

        assertThat(pedidosDoTitular(semPerfil)).isZero();
    }

    @Test
    void exemploDoContratoDaConfirmacaoEhValidoParaOHelperDoTeste() throws Exception {
        try (InputStream exemplo = getClass().getResourceAsStream("/contracts/lgpd.erasure.completed.v1.valid.json")) {
            assertThat(contrato("lgpd.erasure.completed.v1.json").validate(objectMapper.readTree(exemplo))).isEmpty();
        }
    }

    private org.springframework.test.web.servlet.ResultActions solicitar(UUID titularId, String sub, String role,
                                                                         String confirmacao) throws Exception {
        return mvc.perform(post("/v1/titulares/{id}/exclusao", titularId)
                .with(jwt().jwt(jwt -> jwt.subject(sub)).authorities(new SimpleGrantedAuthority("ROLE_" + role)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmacao\": \"" + confirmacao + "\"}"));
    }

    private void publicarUserCreated(UUID perfilId, String sub, String tipo, String email) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("perfilId", perfilId.toString());
        data.put("keycloakSub", sub);
        data.put("tipo", tipo);
        data.put("nome", "Fulano");
        data.put("email", email);
        data.put("criadoEm", "2026-11-01T10:00:00Z");
        publicar(UUID.randomUUID(), "user.created", data);
    }

    private void publicarConfirmacao(UUID eventoId, UUID protocolo, String servico) {
        Map<String, Object> envelope = envelope(eventoId, "lgpd.erasure.completed", "/" + servico,
                protocolo.toString(), Map.of("protocolo", protocolo.toString(), "servico", servico));
        assertThat(contrato("lgpd.erasure.completed.v1.json").validate(objectMapper.valueToTree(envelope))).isEmpty();
        enviar(eventoId, "lgpd.erasure.completed", envelope);
    }

    private void publicar(UUID eventoId, String tipo, Map<String, Object> data) {
        enviar(eventoId, tipo, envelope(eventoId, tipo, "/teste", UUID.randomUUID().toString(), data));
    }

    private static Map<String, Object> envelope(UUID id, String tipo, String source, String subject,
                                                Map<String, Object> data) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("specversion", "1.0");
        envelope.put("id", id.toString());
        envelope.put("source", source);
        envelope.put("type", tipo);
        envelope.put("time", "2026-11-10T12:00:00Z");
        envelope.put("datacontenttype", "application/json");
        envelope.put("dataschema", "https://raw.githubusercontent.com/SeniorInsight-Project/si-contracts/main/events/"
                + tipo + ".v1.json");
        envelope.put("subject", subject);
        envelope.put("data", data);
        return envelope;
    }

    private void enviar(UUID eventoId, String tipo, Map<String, Object> envelope) {
        try {
            rabbitTemplate.send("si.events", tipo, MessageBuilder
                    .withBody(objectMapper.writeValueAsBytes(envelope))
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .setMessageId(eventoId.toString())
                    .setType(tipo)
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private JsonSchema contrato(String arquivo) {
        try (InputStream schema = getClass().getResourceAsStream("/contracts/" + arquivo)) {
            return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(schema);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void semearRelatorio(UUID idosoId, String chave) {
        jdbc.update("""
                INSERT INTO relatorio (idoso_id, tipo, periodo_inicio, periodo_fim, status, objeto_chave)
                VALUES (?, 'SEMANAL_MEDICO', '2026-10-26', '2026-11-01', 'PRONTO', ?)
                """, idosoId, chave);
    }

    private int pedidosDoTitular(UUID titularId) {
        return jdbc.queryForObject("SELECT count(*) FROM pedido_titular WHERE titular_id = ?", Integer.class,
                titularId);
    }

    private int perfis(UUID perfilId) {
        return jdbc.queryForObject("SELECT count(*) FROM projecao_perfil WHERE perfil_id = ?", Integer.class, perfilId);
    }

    private int contarAssinaturas(String sub) {
        return jdbc.queryForObject("SELECT count(*) FROM projecao_assinatura WHERE familiar_sub = ?", Integer.class,
                sub);
    }

    private int contar(String tabela, UUID idosoId) {
        return jdbc.queryForObject("SELECT count(*) FROM " + tabela + " WHERE idoso_id = ?", Integer.class, idosoId);
    }

    private int confirmacoes(UUID protocolo) {
        return jdbc.queryForObject("SELECT count(*) FROM confirmacao_exclusao WHERE pedido_id = ?", Integer.class,
                protocolo);
    }

    private boolean processado(UUID eventoId) {
        return jdbc.queryForObject("SELECT count(*) FROM evento_processado WHERE evento_id = ?", Integer.class,
                eventoId) > 0;
    }

    private boolean existeNoArmazenamento(String chave) throws Exception {
        MinioClient cliente = MinioClient.builder().endpoint(minio.getS3URL())
                .credentials(minio.getUserName(), minio.getPassword()).build();
        try {
            cliente.statObject(StatObjectArgs.builder().bucket(BUCKET).object(chave).build());
            return true;
        } catch (ErrorResponseException e) {
            return false;
        }
    }
}
