package com.seniorinsight.report.domain.projecao;

import java.util.UUID;

import com.seniorinsight.report.domain.exclusao.TipoTitular;

/** Perfil projetado de {@code user.created}: liga o id do titular ao {@code sub} do token. */
public record PerfilTitular(UUID perfilId, String keycloakSub, TipoTitular tipo, String nome, String email) {
}
