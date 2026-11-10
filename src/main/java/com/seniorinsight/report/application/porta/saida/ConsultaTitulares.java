package com.seniorinsight.report.application.porta.saida;

import java.util.Optional;
import java.util.UUID;

import com.seniorinsight.report.domain.projecao.AssinaturaFamiliar;
import com.seniorinsight.report.domain.projecao.PerfilTitular;

/** Consultas às projeções usadas para autorizar e validar o pedido de exclusão. */
public interface ConsultaTitulares {

    Optional<PerfilTitular> buscarPerfil(UUID perfilId);

    Optional<PerfilTitular> buscarPerfilPorSub(String keycloakSub);

    /** O {@code sub} é Familiar Administrador do idoso. */
    boolean ehFamiliarAdministrador(UUID idosoId, String familiarSub);

    Optional<AssinaturaFamiliar> buscarAssinatura(String familiarSub);
}
