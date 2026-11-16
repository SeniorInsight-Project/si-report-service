package com.seniorinsight.report.application.exclusao;

import java.util.Set;
import java.util.UUID;

/** Quem fez a requisição: o {@code sub} e as roles de realm do token. */
public record Solicitante(String sub, Set<String> roles) {

    static final String ROLE_IDOSO = "idoso";
    static final String ROLE_FAMILIAR = "familiar";

    /** O pedido é feito pelo idoso ou por um familiar; as demais roles (médico, admin) não têm esse direito. */
    boolean podeSolicitarExclusao() {
        return roles.contains(ROLE_IDOSO) || roles.contains(ROLE_FAMILIAR);
    }

    /** O {@code titularId} da rota é o {@code sub} do Keycloak de quem está autenticado. */
    boolean ehOTitular(UUID titularId) {
        return sub.equals(titularId.toString());
    }
}
