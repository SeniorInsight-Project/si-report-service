package com.seniorinsight.report.application.porta.saida;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.seniorinsight.report.domain.exclusao.PedidoExclusao;
import com.seniorinsight.report.domain.exclusao.ServicoConfirmador;

public interface RepositorioPedidosExclusao {

    boolean existeEmAndamento(UUID titularId);

    /** Grava o pedido como {@code EM_ANDAMENTO}. Falha se já houver outro em andamento para o titular. */
    void salvar(PedidoExclusao pedido);

    Optional<PedidoExclusao> buscar(UUID protocolo);

    /** Registra a confirmação do serviço; repetir a mesma confirmação não tem efeito. */
    void registrarConfirmacao(UUID protocolo, ServicoConfirmador servico, Instant confirmadoEm);

    Set<ServicoConfirmador> servicosConfirmados(UUID protocolo);

    /**
     * Marca o pedido como concluído e apaga o e-mail do comprovante.
     *
     * @return {@code false} se o pedido já estava concluído
     */
    boolean concluir(UUID protocolo, Instant concluidoEm);
}
