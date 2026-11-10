package com.seniorinsight.report.domain.exclusao;

import java.util.UUID;

import com.seniorinsight.report.domain.evento.EventoDominio;

/**
 * Evento {@code lgpd.erasure.requested}: pede aos outros serviços a exclusão dos dados do titular. Os componentes do
 * record são o {@code data} do evento, como no contrato do si-contracts.
 */
public record ExclusaoSolicitada(UUID protocolo, TipoTitular tipoTitular, UUID titularId, String titularSub)
        implements EventoDominio {

    public static final String TIPO_EVENTO = "lgpd.erasure.requested";

    public static ExclusaoSolicitada de(PedidoExclusao pedido) {
        return new ExclusaoSolicitada(pedido.protocolo(), pedido.tipoTitular(), pedido.titularId(),
                pedido.titularSub());
    }

    @Override
    public String tipoAgregado() {
        return "pedido_titular";
    }

    /** O subject do evento é o protocolo. */
    @Override
    public UUID agregadoId() {
        return protocolo;
    }

    @Override
    public String tipoEvento() {
        return TIPO_EVENTO;
    }
}
