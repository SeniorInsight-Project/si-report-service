package com.seniorinsight.report.application.exclusao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    private final UUID perfilIdoso = UUID.randomUUID();
    private final String subIdoso = idosoId.toString();
    private final UUID idosoCare = UUID.randomUUID();
    private final PerfilTitular idoso =
            new PerfilTitular(perfilIdoso, subIdoso, TipoTitular.IDOSO, "Maria", "maria@email.com", idosoCare);
    private final Solicitante daIdosa = new Solicitante(subIdoso, Set.of("idoso"));

    @BeforeEach
    void preparar() {
        caso = new SolicitarExclusao(titulares, pedidos, publicador, eliminacao, armazenamento,
                Clock.fixed(AGORA, ZoneOffset.UTC));
        when(titulares.buscarPerfilPorSub(subIdoso)).thenReturn(Optional.of(idoso));
        when(eliminacao.apagarRegistros(any())).thenReturn(List.of());
    }

    @Test
    void idosoTitularPedeAExclusaoComPrazoDeQuinzeDias() {
        PedidoExclusao pedido = caso.executar(idosoId, daIdosa);

        assertThat(pedido.titularId()).isEqualTo(perfilIdoso);
        assertThat(pedido.titularSub()).isEqualTo(subIdoso);
        assertThat(pedido.tipoTitular()).isEqualTo(TipoTitular.IDOSO);
        assertThat(pedido.solicitadoEm()).isEqualTo(AGORA);
        assertThat(pedido.prazoEm()).isEqualTo(AGORA.plus(Duration.ofDays(15)));
        assertThat(pedido.emailComprovante()).isEqualTo("maria@email.com");
        verify(pedidos).salvar(pedido);
    }

    @Test
    void idosoComIdosoIdPublicaOEventoComOIdosoIdEmTitularIdEOSubEmTitularSub() {
        PedidoExclusao pedido = caso.executar(idosoId, daIdosa);

        ArgumentCaptor<ExclusaoSolicitada> evento = ArgumentCaptor.forClass(ExclusaoSolicitada.class);
        verify(publicador).publicar(evento.capture());
        assertThat(evento.getValue()).isEqualTo(
                new ExclusaoSolicitada(pedido.protocolo(), TipoTitular.IDOSO, idosoCare, subIdoso));
        assertThat(evento.getValue().agregadoId()).isEqualTo(pedido.protocolo());
        assertThat(evento.getValue().tipoEvento()).isEqualTo("lgpd.erasure.requested");
    }

    @Test
    void idosoSemIdosoIdPublicaOEventoComOIdDoPerfilEmTitularId() {
        PerfilTitular semCare = new PerfilTitular(perfilIdoso, subIdoso, TipoTitular.IDOSO, "Maria", null, null);
        when(titulares.buscarPerfilPorSub(subIdoso)).thenReturn(Optional.of(semCare));

        PedidoExclusao pedido = caso.executar(idosoId, daIdosa);

        verify(publicador).publicar(
                new ExclusaoSolicitada(pedido.protocolo(), TipoTitular.IDOSO, perfilIdoso, subIdoso));
    }

    @Test
    void familiarPublicaOEventoComOIdDoPerfilEmTitularId() {
        UUID familiarId = UUID.randomUUID();
        UUID perfilFamiliar = UUID.randomUUID();
        String subFamiliar = familiarId.toString();
        when(titulares.buscarPerfilPorSub(subFamiliar)).thenReturn(Optional.of(
                new PerfilTitular(perfilFamiliar, subFamiliar, TipoTitular.FAMILIAR, "Ana", "ana@email.com", null)));

        PedidoExclusao pedido = caso.executar(familiarId, new Solicitante(subFamiliar, Set.of("familiar")));

        verify(publicador).publicar(
                new ExclusaoSolicitada(pedido.protocolo(), TipoTitular.FAMILIAR, perfilFamiliar, subFamiliar));
    }

    @Test
    void pedidoGuardaOIdDoPerfilMesmoQuandoOEventoLevaOIdosoId() {
        PedidoExclusao pedido = caso.executar(idosoId, daIdosa);

        assertThat(pedido.titularId()).isEqualTo(perfilIdoso);
    }

    @Test
    void apagaOsArquivosDoTitularNoArmazenamentoDepoisDeGravarOPedido() {
        when(eliminacao.apagarRegistros(idoso)).thenReturn(List.of("relatorios/a.pdf", "exports/b.zip"));

        caso.executar(idosoId, daIdosa);

        var ordem = inOrder(pedidos, eliminacao, armazenamento);
        ordem.verify(pedidos).salvar(any());
        ordem.verify(eliminacao).apagarRegistros(idoso);
        ordem.verify(armazenamento).remover("relatorios/a.pdf");
        ordem.verify(armazenamento).remover("exports/b.zip");
    }

    @Test
    void titularIdDiferenteDoSubDoTokenEhAcessoNegadoSemConsultarNemRegistrarNemPublicar() {
        UUID deOutraPessoa = UUID.randomUUID();

        assertThatThrownBy(() -> caso.executar(deOutraPessoa, daIdosa))
                .isInstanceOfSatisfying(ExclusaoException.class, e -> assertThat(e.tipo()).isEqualTo(Tipo.ACESSO_NEGADO));
        verifyNoInteractions(titulares, pedidos, publicador, eliminacao, armazenamento);
    }

    @Test
    void familiarAdministradorNaoPedePorOutraPessoa() {
        Solicitante familiar = new Solicitante(UUID.randomUUID().toString(), Set.of("familiar"));

        assertThatThrownBy(() -> caso.executar(idosoId, familiar))
                .isInstanceOfSatisfying(ExclusaoException.class, e -> assertThat(e.tipo()).isEqualTo(Tipo.ACESSO_NEGADO));
        verify(pedidos, never()).salvar(any());
        verify(publicador, never()).publicar(any());
    }

    @Test
    void subSemPerfilRetornaNaoEncontrado() {
        UUID semPerfil = UUID.randomUUID();
        when(titulares.buscarPerfilPorSub(semPerfil.toString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> caso.executar(semPerfil, new Solicitante(semPerfil.toString(), Set.of("idoso"))))
                .isInstanceOfSatisfying(ExclusaoException.class,
                        e -> assertThat(e.tipo()).isEqualTo(Tipo.TITULAR_NAO_ENCONTRADO));
        verify(pedidos, never()).salvar(any());
        verify(publicador, never()).publicar(any());
    }

    @Test
    void medicoOuAdminNaoPedemMesmoComOSubDoTitular() {
        assertThatThrownBy(() -> caso.executar(idosoId, new Solicitante(subIdoso, Set.of("medico"))))
                .isInstanceOfSatisfying(ExclusaoException.class, e -> assertThat(e.tipo()).isEqualTo(Tipo.ACESSO_NEGADO));
    }

    @Test
    void pedidoEmAndamentoEhConflito() {
        when(pedidos.existeEmAndamento(perfilIdoso)).thenReturn(true);

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
        String subFamiliar = familiarId.toString();
        when(titulares.buscarPerfilPorSub(subFamiliar)).thenReturn(Optional.of(
                new PerfilTitular(UUID.randomUUID(), subFamiliar, TipoTitular.FAMILIAR, "Ana", "ana@email.com", null)));
        when(titulares.buscarAssinatura(subFamiliar))
                .thenReturn(Optional.of(new AssinaturaFamiliar(subFamiliar, "PREMIUM", true)));

        assertThatThrownBy(() -> caso.executar(familiarId, new Solicitante(subFamiliar, Set.of("familiar"))))
                .isInstanceOfSatisfying(ExclusaoException.class, e -> assertThat(e.tipo()).isEqualTo(Tipo.CONFLITO));
    }

    @Test
    void familiarComAssinaturaCanceladaPodePedir() {
        UUID familiarId = UUID.randomUUID();
        String subFamiliar = familiarId.toString();
        when(titulares.buscarPerfilPorSub(subFamiliar)).thenReturn(Optional.of(
                new PerfilTitular(UUID.randomUUID(), subFamiliar, TipoTitular.FAMILIAR, "Ana", "ana@email.com", null)));
        when(titulares.buscarAssinatura(subFamiliar))
                .thenReturn(Optional.of(new AssinaturaFamiliar(subFamiliar, "PREMIUM", false)));

        PedidoExclusao pedido = caso.executar(familiarId, new Solicitante(subFamiliar, Set.of("familiar")));

        assertThat(pedido.tipoTitular()).isEqualTo(TipoTitular.FAMILIAR);
    }
}
