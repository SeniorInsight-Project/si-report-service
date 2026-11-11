package com.seniorinsight.report.domain.projecao;

/** Assinatura do familiar, projetada de {@code subscription.changed}. */
public record AssinaturaFamiliar(String familiarSub, String plano, boolean ativa) {

    public boolean premiumAtiva() {
        return ativa && "PREMIUM".equals(plano);
    }
}
