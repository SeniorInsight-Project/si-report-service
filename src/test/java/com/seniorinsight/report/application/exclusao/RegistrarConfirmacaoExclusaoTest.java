package com.seniorinsight.report.application.exclusao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.seniorinsight.report.application.porta.saida.EnviadorComprovante;
import com.seniorinsight.report.application.porta.saida.RegistroEventosProcessados;
import com.seniorinsight.report.application.porta.saida.RepositorioPedidosExclusao;
import com.seniorinsight.report.domain.exclusao.PedidoExclusao;
import com.seniorinsight.report.domain.exclusao.ServicoConfirmador;
import com.seniorinsight.report.domain.exclusao.TipoTitular;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RegistrarConfirmacaoExclusaoTest {

    private static final Instant AGORA = Instant.parse("2026-11-12T09:00:00Z");
    private static final Instant SOLICITADO = Instant.parse("2026-11-10T12:00:00Z");

    private final RegistroEventosProcessados eventos = mock(RegistroEventosProcessados.class);
    private final RepositorioPedidosExclusao pedidos = mock(RepositorioPedidosExclusao.class);
    private final EnviadorComprovante enviador = mock(EnviadorComprovante.class);

    private RegistrarConfirmacaoExclusao caso;

    private final UUID protocolo = UUID.randomUUID();
    private final UUID eventoId = UUID.randomUUID();
    private final PedidoExclusao pedido = new PedidoExclusao(protocolo, UUID.randomUUID(), TipoTitular.IDOSO,
            "sub", "sub", "maria@email.com", SOLICITADO, SOLICITADO.plusSeconds(15 * 86400));

    @BeforeEach
    void preparar() {
        caso = new RegistrarConfirmacaoExclusao(eventos, pedidos, enviador, Clock.fixed(AGORA, ZoneOffset.UTC));
        when(eventos.registrarSeNovo(any(), any())).thenReturn(true);
        when(pedidos.buscar(protocolo)).thenReturn(Optional.of(pedido));
    }

    @Test
    void confirmacaoParcialSoRegistraSemEnviarOComprovante() {
        when(pedidos.servicosConfirmados(protocolo)).thenReturn(Set.of(ServicoConfirmador.IDENTITY));

        caso.executar(eventoId, "lgpd.erasure.completed", protocolo, "identity-service");

        verify(pedidos).registrarConfirmacao(protocolo, ServicoConfirmador.IDENTITY, AGORA);
        verify(pedidos, never()).concluir(any(), any());
        verify(enviador, never()).enviar(any(), any(), any(), any());
    }

    @Test
    void ultimaConfirmacaoConcluiOPedidoEEnviaOComprovante() {
        when(pedidos.servicosConfirmados(protocolo)).thenReturn(EnumSet.allOf(ServicoConfirmador.class));
        when(pedidos.concluir(protocolo, AGORA)).thenReturn(true);

        caso.executar(eventoId, "lgpd.erasure.completed", protocolo, "telemetry-service");

        verify(enviador).enviar("maria@email.com", protocolo, SOLICITADO, AGORA);
    }

    @Test
    void pedidoJaConcluidoNaoReenviaOComprovante() {
        when(pedidos.servicosConfirmados(protocolo)).thenReturn(EnumSet.allOf(ServicoConfirmador.class));
        when(pedidos.concluir(protocolo, AGORA)).thenReturn(false);

        caso.executar(eventoId, "lgpd.erasure.completed", protocolo, "telemetry-service");

        verify(enviador, never()).enviar(any(), any(), any(), any());
    }

    @Test
    void eventoRepetidoEhIgnorado() {
        when(eventos.registrarSeNovo(eventoId, "lgpd.erasure.completed")).thenReturn(false);

        assertThat(caso.executar(eventoId, "lgpd.erasure.completed", protocolo, "identity-service")).isFalse();
        verify(pedidos, never()).registrarConfirmacao(any(), any(), any());
    }

    @Test
    void servicoDesconhecidoOuProtocoloDesconhecidoSaoIgnorados() {
        caso.executar(eventoId, "lgpd.erasure.completed", protocolo, "outro-service");
        UUID desconhecido = UUID.randomUUID();
        when(pedidos.buscar(desconhecido)).thenReturn(Optional.empty());
        caso.executar(UUID.randomUUID(), "lgpd.erasure.completed", desconhecido, "identity-service");

        verify(pedidos, never()).registrarConfirmacao(any(), any(), any());
    }

    @Test
    void semEmailConcluiSemEnviar() {
        PedidoExclusao semEmail = new PedidoExclusao(protocolo, pedido.titularId(), TipoTitular.IDOSO, "sub", "sub",
                null, SOLICITADO, pedido.prazoEm());
        when(pedidos.buscar(protocolo)).thenReturn(Optional.of(semEmail));
        when(pedidos.servicosConfirmados(protocolo)).thenReturn(EnumSet.allOf(ServicoConfirmador.class));
        when(pedidos.concluir(protocolo, AGORA)).thenReturn(true);

        caso.executar(eventoId, "lgpd.erasure.completed", protocolo, "telemetry-service");

        verify(enviador, never()).enviar(any(), any(), any(), any());
    }
}
