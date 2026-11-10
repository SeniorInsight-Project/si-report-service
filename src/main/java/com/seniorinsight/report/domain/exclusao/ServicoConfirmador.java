package com.seniorinsight.report.domain.exclusao;

import java.util.Arrays;
import java.util.Optional;

/**
 * Serviços que confirmam, cada um por conta própria, a exclusão da sua parte dos dados
 * ({@code lgpd.erasure.completed}). O report-service não está na lista: ele apaga os seus dados ao registrar o pedido.
 */
public enum ServicoConfirmador {
    IDENTITY("identity-service"),
    CARE("care-service"),
    BILLING("billing-service"),
    INSIGHT("insight-service"),
    NOTIFICATION("notification-service"),
    TELEMETRY("telemetry-service");

    private final String nome;

    ServicoConfirmador(String nome) {
        this.nome = nome;
    }

    /** Nome como aparece no campo {@code servico} do evento. */
    public String nome() {
        return nome;
    }

    public static Optional<ServicoConfirmador> porNome(String nome) {
        return Arrays.stream(values()).filter(servico -> servico.nome.equals(nome)).findFirst();
    }
}
