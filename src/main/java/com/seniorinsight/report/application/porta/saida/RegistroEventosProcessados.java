package com.seniorinsight.report.application.porta.saida;

import java.util.UUID;

/** Deduplicação dos eventos recebidos: a entrega do broker é "pelo menos uma vez". */
public interface RegistroEventosProcessados {

    /**
     * Registra o evento na transação corrente.
     *
     * @return {@code false} se o evento já tinha sido processado
     */
    boolean registrarSeNovo(UUID eventoId, String tipoEvento);
}
