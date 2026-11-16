package com.seniorinsight.report.application.exclusao;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import com.seniorinsight.report.application.porta.saida.ArmazenamentoObjetos;
import com.seniorinsight.report.application.porta.saida.ConsultaTitulares;
import com.seniorinsight.report.application.porta.saida.EliminacaoDadosTitular;
import com.seniorinsight.report.application.porta.saida.PublicadorEventos;
import com.seniorinsight.report.application.porta.saida.RepositorioPedidosExclusao;
import com.seniorinsight.report.domain.exclusao.ExclusaoSolicitada;
import com.seniorinsight.report.domain.exclusao.PedidoExclusao;
import com.seniorinsight.report.domain.exclusao.TipoTitular;
import com.seniorinsight.report.domain.projecao.PerfilTitular;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pedido de exclusão dos dados do titular (HU-022, RF-015).
 *
 * <p>Só o próprio titular pede: o {@code titularId} da rota é o {@code sub} do Keycloak e precisa ser o do token. O
 * pedido de um Familiar Administrador em nome de outra pessoa não existe no MVP. Numa única transação o caso de uso
 * grava o pedido, com o id do perfil como titular (prazo de {@value PedidoExclusao#PRAZO_DIAS} dias), enfileira
 * {@code lgpd.erasure.requested} na outbox, com a chave de domínio do titular, e apaga os dados do titular no próprio
 * report-service: registros no banco e arquivos (PDFs e exportações) no armazenamento. Se o armazenamento falhar, nada é confirmado e o pedido pode ser refeito.
 */
@Service
public class SolicitarExclusao {

    private static final Logger log = LoggerFactory.getLogger(SolicitarExclusao.class);

    private final ConsultaTitulares titulares;
    private final RepositorioPedidosExclusao pedidos;
    private final PublicadorEventos publicador;
    private final EliminacaoDadosTitular eliminacao;
    private final ArmazenamentoObjetos armazenamento;
    private final Clock relogio;

    public SolicitarExclusao(ConsultaTitulares titulares, RepositorioPedidosExclusao pedidos,
                             PublicadorEventos publicador, EliminacaoDadosTitular eliminacao,
                             ArmazenamentoObjetos armazenamento, Clock relogio) {
        this.titulares = titulares;
        this.pedidos = pedidos;
        this.publicador = publicador;
        this.eliminacao = eliminacao;
        this.armazenamento = armazenamento;
        this.relogio = relogio;
    }

    @Transactional
    public PedidoExclusao executar(UUID titularId, Solicitante solicitante) {
        if (!solicitante.podeSolicitarExclusao() || !solicitante.ehOTitular(titularId)) {
            throw ExclusaoException.acessoNegado();
        }
        PerfilTitular titular = titulares.buscarPerfilPorSub(solicitante.sub())
                .orElseThrow(ExclusaoException::titularNaoEncontrado);
        if (pedidos.existeEmAndamento(titular.perfilId())) {
            throw ExclusaoException.pedidoEmAndamento();
        }
        if (titular.tipo() == TipoTitular.FAMILIAR && temPremiumAtiva(titular)) {
            throw ExclusaoException.assinaturaAtiva();
        }

        PedidoExclusao pedido = PedidoExclusao.registrar(titular.perfilId(), titular.tipo(), titular.keycloakSub(),
                solicitante.sub(), titular.email(), relogio.instant());
        try {
            pedidos.salvar(pedido);
        } catch (DuplicateKeyException e) {
            // Duas requisições simultâneas: o índice único deixa passar só a primeira.
            throw ExclusaoException.pedidoEmAndamento();
        }
        publicador.publicar(ExclusaoSolicitada.de(pedido, titular));

        List<String> arquivos = eliminacao.apagarRegistros(titular);
        arquivos.forEach(armazenamento::remover);
        log.info("Exclusão {}: pedido registrado, {} arquivo(s) apagado(s) do armazenamento",
                pedido.protocolo(), arquivos.size());
        return pedido;
    }

    private boolean temPremiumAtiva(PerfilTitular familiar) {
        return titulares.buscarAssinatura(familiar.keycloakSub())
                .map(assinatura -> assinatura.premiumAtiva())
                .orElse(false);
    }
}
