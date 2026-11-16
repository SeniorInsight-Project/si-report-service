package com.seniorinsight.report.application.porta.saida;

import java.util.Optional;

import com.seniorinsight.report.domain.projecao.AssinaturaFamiliar;
import com.seniorinsight.report.domain.projecao.PerfilTitular;

/** Consultas às projeções usadas para autorizar e validar o pedido de exclusão. */
public interface ConsultaTitulares {

    Optional<PerfilTitular> buscarPerfilPorSub(String keycloakSub);

    Optional<AssinaturaFamiliar> buscarAssinatura(String familiarSub);
}
