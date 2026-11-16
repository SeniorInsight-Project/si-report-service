package com.seniorinsight.report.domain.projecao;

import java.util.UUID;

import com.seniorinsight.report.domain.exclusao.TipoTitular;

/**
 * Perfil projetado de {@code user.created}: liga o id do titular ao {@code sub} do token.
 *
 * @param idosoId id do idoso no care-service; nulo para familiar e para idoso sem cadastro no care
 */
public record PerfilTitular(UUID perfilId, String keycloakSub, TipoTitular tipo, String nome, String email,
                            UUID idosoId) {

    /**
     * Chave com que os outros serviços guardam os dados do titular: o {@code idosoId} do care para o idoso cadastrado
     * lá; nos demais casos, o id do perfil.
     */
    public UUID chaveDeDominio() {
        return tipo == TipoTitular.IDOSO && idosoId != null ? idosoId : perfilId;
    }
}
