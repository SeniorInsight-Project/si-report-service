package com.seniorinsight.report.adapters.in.web;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;

import com.seniorinsight.report.application.exclusao.Solicitante;
import com.seniorinsight.report.application.exclusao.SolicitarExclusao;
import com.seniorinsight.report.domain.exclusao.PedidoExclusao;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Rota {@code POST /v1/titulares/{titularId}/exclusao} do contrato {@code report-v1} (gateway: {@code /api/v1/report/**}). */
@RestController
class ExclusaoController {

    private final SolicitarExclusao solicitarExclusao;

    ExclusaoController(SolicitarExclusao solicitarExclusao) {
        this.solicitarExclusao = solicitarExclusao;
    }

    @PostMapping("/v1/titulares/{titularId}/exclusao")
    @ResponseStatus(HttpStatus.ACCEPTED)
    PedidoExclusaoResponse solicitar(@PathVariable UUID titularId,
                                     @Valid @RequestBody SolicitarExclusaoRequest requisicao,
                                     Authentication autenticacao) {
        Jwt jwt = (Jwt) autenticacao.getPrincipal();
        // As roles vêm das authorities ROLE_* montadas pelo ConversorRolesKeycloak.
        var roles = autenticacao.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith(ConversorRolesKeycloak.PREFIXO))
                .map(authority -> authority.substring(ConversorRolesKeycloak.PREFIXO.length()))
                .collect(Collectors.toSet());

        PedidoExclusao pedido = solicitarExclusao.executar(titularId, new Solicitante(jwt.getSubject(), roles));
        return new PedidoExclusaoResponse(pedido.protocolo().toString(), PedidoExclusao.PRAZO_DIAS,
                pedido.solicitadoEm());
    }

    record SolicitarExclusaoRequest(
            @NotNull(message = "confirmacao é obrigatória")
            @Pattern(regexp = "EXCLUIR", message = "confirmacao deve ser EXCLUIR") String confirmacao) {
    }

    record PedidoExclusaoResponse(String protocolo, int prazoDias, Instant solicitadoEm) {
    }
}
