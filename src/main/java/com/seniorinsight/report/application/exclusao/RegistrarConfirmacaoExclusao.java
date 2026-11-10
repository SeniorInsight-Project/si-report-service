package com.seniorinsight.report.application.exclusao;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.seniorinsight.report.application.porta.saida.EnviadorComprovante;
import com.seniorinsight.report.application.porta.saida.RegistroEventosProcessados;
import com.seniorinsight.report.application.porta.saida.RepositorioPedidosExclusao;
import com.seniorinsight.report.domain.exclusao.PedidoExclusao;
import com.seniorinsight.report.domain.exclusao.ServicoConfirmador;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Acompanha as confirmações {@code lgpd.erasure.completed}. Quando todos os serviços confirmaram, conclui o pedido e
 * envia o comprovante por e-mail.
 *
 * <p>Tudo roda na transação do consumidor: se o envio do comprovante falhar, a confirmação não fica gravada e a
 * mensagem vai para a dead-letter, de onde pode ser reprocessada.
 */
@Service
public class RegistrarConfirmacaoExclusao {

    private static final Logger log = LoggerFactory.getLogger(RegistrarConfirmacaoExclusao.class);

    private final RegistroEventosProcessados eventosProcessados;
    private final RepositorioPedidosExclusao pedidos;
    private final EnviadorComprovante enviador;
    private final Clock relogio;

    public RegistrarConfirmacaoExclusao(RegistroEventosProcessados eventosProcessados,
                                        RepositorioPedidosExclusao pedidos, EnviadorComprovante enviador,
                                        Clock relogio) {
        this.eventosProcessados = eventosProcessados;
        this.pedidos = pedidos;
        this.enviador = enviador;
        this.relogio = relogio;
    }

    /** @return {@code false} se o evento já tinha sido processado */
    @Transactional
    public boolean executar(UUID eventoId, String tipoEvento, UUID protocolo, String nomeServico) {
        if (!eventosProcessados.registrarSeNovo(eventoId, tipoEvento)) {
            return false;
        }
        Optional<ServicoConfirmador> servico = ServicoConfirmador.porNome(nomeServico);
        if (servico.isEmpty()) {
            log.warn("Exclusão {}: confirmação de serviço desconhecido '{}' ignorada", protocolo, nomeServico);
            return true;
        }
        Optional<PedidoExclusao> pedido = pedidos.buscar(protocolo);
        if (pedido.isEmpty()) {
            log.warn("Exclusão {}: confirmação de {} para protocolo desconhecido ignorada", protocolo, nomeServico);
            return true;
        }

        Instant agora = relogio.instant();
        pedidos.registrarConfirmacao(protocolo, servico.get(), agora);
        Set<ServicoConfirmador> confirmados = pedidos.servicosConfirmados(protocolo);
        if (confirmados.containsAll(Arrays.asList(ServicoConfirmador.values())) && pedidos.concluir(protocolo, agora)) {
            log.info("Exclusão {}: todos os serviços confirmaram, enviando o comprovante", protocolo);
            enviarComprovante(pedido.get(), agora);
        }
        return true;
    }

    private void enviarComprovante(PedidoExclusao pedido, Instant concluidoEm) {
        if (pedido.emailComprovante() == null) {
            log.warn("Exclusão {}: sem e-mail para o comprovante", pedido.protocolo());
            return;
        }
        enviador.enviar(pedido.emailComprovante(), pedido.protocolo(), pedido.solicitadoEm(), concluidoEm);
    }
}
