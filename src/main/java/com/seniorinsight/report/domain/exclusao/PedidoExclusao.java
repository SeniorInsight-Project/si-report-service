package com.seniorinsight.report.domain.exclusao;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Pedido de exclusão dos dados de um titular (LGPD). O protocolo é o id do pedido.
 *
 * @param titularId       {@code sub} do Keycloak do titular, como UUID
 * @param solicitanteSub  {@code sub} de quem fez o pedido, sempre o do próprio titular
 * @param emailComprovante destino do comprovante; some do banco depois que o comprovante é enviado
 */
public record PedidoExclusao(
        UUID protocolo,
        UUID titularId,
        TipoTitular tipoTitular,
        String titularSub,
        String solicitanteSub,
        String emailComprovante,
        Instant solicitadoEm,
        Instant prazoEm) {

    public static final int PRAZO_DIAS = 15;

    public static PedidoExclusao registrar(UUID titularId, TipoTitular tipoTitular, String titularSub,
                                           String solicitanteSub, String emailComprovante, Instant agora) {
        return new PedidoExclusao(UUID.randomUUID(), titularId, tipoTitular, titularSub, solicitanteSub,
                emailComprovante, agora, agora.plus(Duration.ofDays(PRAZO_DIAS)));
    }
}
