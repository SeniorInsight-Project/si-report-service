package com.seniorinsight.report.adapters.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.seniorinsight.report.application.exclusao.ExclusaoException;
import com.seniorinsight.report.application.exclusao.Solicitante;
import com.seniorinsight.report.application.exclusao.SolicitarExclusao;
import com.seniorinsight.report.domain.exclusao.PedidoExclusao;
import com.seniorinsight.report.domain.exclusao.TipoTitular;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(controllers = ExclusaoController.class,
        properties = "KEYCLOAK_ISSUER_URI=http://keycloak.invalid/realms/seniorinsight")
@Import({SegurancaConfig.class, TratadorErros.class})
class ExclusaoControllerTest {

    private static final String CORPO_VALIDO = "{\"confirmacao\": \"EXCLUIR\"}";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    SolicitarExclusao solicitarExclusao;

    @MockitoBean
    JwtDecoder jwtDecoder;

    private final UUID titularId = UUID.randomUUID();

    private RequestPostProcessor token(String sub, String... roles) {
        return jwt().jwt(jwt -> jwt.subject(sub))
                .authorities(java.util.Arrays.stream(roles)
                        .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role)).toList());
    }

    @Test
    void responde202ComOProtocoloOPrazoEADataDoPedido() throws Exception {
        UUID protocolo = UUID.randomUUID();
        Instant agora = Instant.parse("2026-11-10T12:00:00Z");
        when(solicitarExclusao.executar(eq(titularId), any())).thenReturn(new PedidoExclusao(protocolo, titularId,
                TipoTitular.IDOSO, titularId.toString(), titularId.toString(), "maria@email.com", agora, agora.plusSeconds(1296000)));

        mvc.perform(post("/v1/titulares/{id}/exclusao", titularId)
                        .with(token(titularId.toString(), "idoso"))
                        .contentType(MediaType.APPLICATION_JSON).content(CORPO_VALIDO))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.protocolo").value(protocolo.toString()))
                .andExpect(jsonPath("$.prazoDias").value(15))
                .andExpect(jsonPath("$.solicitadoEm").value("2026-11-10T12:00:00Z"));

        verify(solicitarExclusao).executar(titularId, new Solicitante(titularId.toString(), Set.of("idoso")));
    }

    @Test
    void semTokenResponde401() throws Exception {
        mvc.perform(post("/v1/titulares/{id}/exclusao", titularId)
                        .contentType(MediaType.APPLICATION_JSON).content(CORPO_VALIDO))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(solicitarExclusao);
    }

    @Test
    void confirmacaoDiferenteDeExcluirResponde400() throws Exception {
        mvc.perform(post("/v1/titulares/{id}/exclusao", titularId)
                        .with(token("sub", "idoso"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"confirmacao\": \"excluir\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("confirmacao deve ser EXCLUIR"))
                .andExpect(jsonPath("$.path").value("/v1/titulares/" + titularId + "/exclusao"));
        verifyNoInteractions(solicitarExclusao);
    }

    @Test
    void corpoSemConfirmacaoOuIdInvalidoResponde400() throws Exception {
        mvc.perform(post("/v1/titulares/{id}/exclusao", titularId)
                        .with(token("sub", "idoso"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/v1/titulares/nao-eh-uuid/exclusao")
                        .with(token("sub", "idoso"))
                        .contentType(MediaType.APPLICATION_JSON).content(CORPO_VALIDO))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(solicitarExclusao);
    }

    @Test
    void acessoNegadoResponde403() throws Exception {
        when(solicitarExclusao.executar(any(), any())).thenThrow(ExclusaoException.acessoNegado());

        mvc.perform(post("/v1/titulares/{id}/exclusao", titularId)
                        .with(token(UUID.randomUUID().toString(), "familiar"))
                        .contentType(MediaType.APPLICATION_JSON).content(CORPO_VALIDO))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void titularDesconhecidoResponde404() throws Exception {
        when(solicitarExclusao.executar(any(), any())).thenThrow(ExclusaoException.titularNaoEncontrado());

        mvc.perform(post("/v1/titulares/{id}/exclusao", titularId)
                        .with(token("sub", "idoso"))
                        .contentType(MediaType.APPLICATION_JSON).content(CORPO_VALIDO))
                .andExpect(status().isNotFound());
    }

    @Test
    void pedidoEmAndamentoOuAssinaturaAtivaResponde409() throws Exception {
        when(solicitarExclusao.executar(any(), any())).thenThrow(ExclusaoException.pedidoEmAndamento());

        mvc.perform(post("/v1/titulares/{id}/exclusao", titularId)
                        .with(token("sub", "idoso"))
                        .contentType(MediaType.APPLICATION_JSON).content(CORPO_VALIDO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"));
    }
}
