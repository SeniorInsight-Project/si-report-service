package com.seniorinsight.report.application.exclusao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.seniorinsight.report.application.exclusao.ExclusaoException.Tipo;
import com.seniorinsight.report.application.porta.saida.ArmazenamentoObjetos;
import com.seniorinsight.report.application.porta.saida.ConsultaTitulares;
import com.seniorinsight.report.application.porta.saida.EliminacaoDadosTitular;
import com.seniorinsight.report.application.porta.saida.PublicadorEventos;
import com.seniorinsight.report.application.porta.saida.RepositorioPedidosExclusao;
import com.seniorinsight.report.domain.exclusao.ExclusaoSolicitada;
import com.seniorinsight.report.domain.exclusao.PedidoExclusao;
import com.seniorinsight.report.domain.exclusao.TipoTitular;
import com.seniorinsight.report.domain.projecao.AssinaturaFamiliar;
import com.seniorinsight.report.domain.projecao.PerfilTitular;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

class SolicitarExclusaoTest {

    private static final Instant AGORA = Instant.parse("2026-11-10T12:00:00Z");

    private final ConsultaTitulares titulares = mock(ConsultaTitulares.class);
    private final RepositorioPedidosExclusao pedidos = mock(RepositorioPedidosExclusao.class);
    private final PublicadorEventos publicador = mock(PublicadorEventos.class);
    private final EliminacaoDadosTitular eliminacao = mock(EliminacaoDadosTitular.class);
    private final ArmazenamentoObjetos armazenamento = mock(ArmazenamentoObjetos.class);

    private SolicitarExclusao caso;

    private final UUID idosoId = UUID.randomUUID();
    private final PerfilTitular idoso =
            new PerfilTitular(idosoId, "sub-idoso", TipoTitular.IDOSO, "Maria", "maria@email.com");
    private final Solicitante daIdosa = new Solicitante("sub-idoso", Set.of("idoso"));

    @BeforeEach
    void preparar() {
        caso = new SolicitarExclusao(titulares, pedidos, publicador, eliminacao, armazenamento,
                Clock.fixed(AGORA, ZoneOffset.UTC));
        when(titulares.buscarPerfil(idosoId)).thenReturn(Optional.of(idoso));
        when(eliminacao.apagarRegistros(any(), any())).thenReturn(List.of());
    }

    @Test
    void idosoTitularPedeAExclusaoComPrazoDeQuinzeDias() {
        PedidoExclusao pedido = caso.executar(idosoId, daIdosa);

        assertThat(pedido.titularId()).isEqualTo(idosoId);
        assertThat(pedido.tipoTitular()).isEqualTo(TipoTitular.IDOSO);
        assertThat(pedido.solicitadoEm()).isEqualTo(AGORA);
        assertThat(pedido.prazoEm()).isEqualTo(AGORA.plus(Duration.ofDays(15)));
        assertThat(pedido.emailComprovante()).isEqualTo("maria@email.com");
        verify(pedidos).salvar(pedido);
    }

    @Test
    void publicaOEventoComOProtocoloENaoComOsDadosDeQuemSolicitou() {
        PedidoExclusao pedido = caso.executar(idosoId, daIdosa);

        ArgumentCaptor<ExclusaoSolicitada> evento = ArgumentCaptor.forClass(ExclusaoSolicitada.class);
        verify(publicador).publicar(evento.capture());
        assertThat(evento.getValue()).isEqualTo(
                new ExclusaoSolicitada(pedido.protocolo(), TipoTitular.IDOSO, idosoId, "sub-idoso"));
        assertThat(evento.getValue().agregadoId()).isEqualTo(pedido.protocolo());
        assertThat(evento.getValue().tipoEvento()).isEqualTo("lgpd.erasure.requested");
    }

    @Test
    void apagaOsArquivosDoTitularNoArmazenamentoDepoisDeGravarOPedido() {
        when(eliminacao.apagarRegistros(idosoId, "sub-idoso")).thenReturn(List.of("relatorios/a.pdf", "exports/b.zip"));

        caso.executar(idosoId, daIdosa);

        var ordem = inOrder(pedidos, eliminacao, armazenamento);
        ordem.verify(pedidos).salvar(any());
        ordem.verify(eliminacao).apagarRegistros(idosoId, "sub-idoso");
        ordem.verify(armazenamento).remover("relatorios/a.pdf");
        ordem.verify(armazenamento).remover("exports/b.zip");
    }

    @Test
    void familiarAdministradorPedePeloIdoso() {
        when(titulares.ehFamiliarAdministrador(idosoId, "sub-familiar")).thenReturn(true);

        PedidoExclusao pedido = caso.executar(idosoId, new Solicitante("sub-familiar", Set.of("familiar")));

        assertThat(pedido.titularSub()).isEqualTo("sub-idoso");
        assertThat(pedido.solicitanteSub()).isEqualTo("sub-familiar");
    }

    @Test
    void idosoSemEmailRecebeOComprovanteNoEmailDeQuemPediu() {
        PerfilTitular semEmail = new PerfilTitular(idosoId, "sub-idoso", TipoTitular.IDOSO, "Maria", null);
        when(titulares.buscarPerfil(idosoId)).thenReturn(Optional.of(semEmail));
        when(titulares.ehFamiliarAdministrador(idosoId, "sub-familiar")).thenReturn(true);
        when(titulares.buscarPerfilPorSub("sub-familiar")).thenReturn(Optional.of(
                new PerfilTitular(UUID.randomUUID(), "sub-familiar", TipoTitular.FAMILIAR, "Ana", "ana@email.com")));

        PedidoExclusao pedido = caso.executar(idosoId, new Solicitante("sub-familiar", Set.of("familiar")));

        assertThat(pedido.emailComprovante()).isEqualTo("ana@email.com");
    }

    @Test
    void familiarComumNaoPedePeloIdoso() {
        when(titulares.ehFamiliarAdministrador(idosoId, "sub-outro")).thenReturn(false);

        assertThatThrownBy(() -> caso.executar(idosoId, new Solicitante("sub-outro", Set.of("familiar"))))
                .isInstanceOfSatisfying(ExclusaoException.class, e -> assertThat(e.tipo()).isEqualTo(Tipo.ACESSO_NEGADO));
        verify(pedidos, never()).salvar(any());
        verify(publicador, never()).publicar(any());
    }

    @Test
    void medicoOuAdminNaoPedemMesmoComOSubDoTitular() {
        assertThatThrownBy(() -> caso.executar(idosoId, new Solicitante("sub-idoso", Set.of("medico"))))
                .isInstanceOfSatisfying(ExclusaoException.class, e -> assertThat(e.tipo()).isEqualTo(Tipo.ACESSO_NEGADO));
    }

    @Test
    void titularDesconhecidoRetornaNaoEncontrado() {
        UUID outro = UUID.randomUUID();
        when(titulares.buscarPerfil(outro)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> caso.executar(outro, daIdosa))
                .isInstanceOfSatisfying(ExclusaoException.class,
                        e -> assertThat(e.tipo()).isEqualTo(Tipo.TITULAR_NAO_ENCONTRADO));
    }

    @Test
    void pedidoEmAndamentoEhConflito() {
        when(pedidos.existeEmAndamento(idosoId)).thenReturn(true);

        assertThatThrownBy(() -> caso.executar(idosoId, daIdosa))
                .isInstanceOfSatisfying(ExclusaoException.class, e -> assertThat(e.tipo()).isEqualTo(Tipo.CONFLITO));
        verify(publicador, never()).publicar(any());
    }

    @Test
    void pedidoSimultaneoBarradoPeloIndiceUnicoTambemEhConflito() {
        doThrow(new DuplicateKeyException("uk")).when(pedidos).salvar(any());

        assertThatThrownBy(() -> caso.executar(idosoId, daIdosa))
                .isInstanceOfSatisfying(ExclusaoException.class, e -> assertThat(e.tipo()).isEqualTo(Tipo.CONFLITO));
        verify(publicador, never()).publicar(any());
    }

    @Test
    void familiarComAssinaturaPremiumAtivaEhConflito() {
        UUID familiarId = UUID.randomUUID();
        when(titulares.buscarPerfil(familiarId)).thenReturn(Optional.of(
                new PerfilTitular(familiarId, "sub-familiar", TipoTitular.FAMILIAR, "Ana", "ana@email.com")));
        when(titulares.buscarAssinatura("sub-familiar"))
                .thenReturn(Optional.of(new AssinaturaFamiliar("sub-familiar", "PREMIUM", true)));

        assertThatThrownBy(() -> caso.executar(familiarId, new Solicitante("sub-familiar", Set.of("familiar"))))
                .isInstanceOfSatisfying(ExclusaoException.class, e -> assertThat(e.tipo()).isEqualTo(Tipo.CONFLITO));
    }

    @Test
    void familiarComAssinaturaCanceladaPodePedir() {
        UUID familiarId = UUID.randomUUID();
        when(titulares.buscarPerfil(familiarId)).thenReturn(Optional.of(
                new PerfilTitular(familiarId, "sub-familiar", TipoTitular.FAMILIAR, "Ana", "ana@email.com")));
        when(titulares.buscarAssinatura("sub-familiar"))
                .thenReturn(Optional.of(new AssinaturaFamiliar("sub-familiar", "PREMIUM", false)));

        PedidoExclusao pedido = caso.executar(familiarId, new Solicitante("sub-familiar", Set.of("familiar")));

        assertThat(pedido.tipoTitular()).isEqualTo(TipoTitular.FAMILIAR);
    }
}
