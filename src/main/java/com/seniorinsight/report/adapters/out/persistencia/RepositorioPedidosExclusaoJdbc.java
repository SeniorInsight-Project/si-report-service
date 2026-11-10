package com.seniorinsight.report.adapters.out.persistencia;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.seniorinsight.report.application.porta.saida.RepositorioPedidosExclusao;
import com.seniorinsight.report.domain.exclusao.PedidoExclusao;
import com.seniorinsight.report.domain.exclusao.ServicoConfirmador;
import com.seniorinsight.report.domain.exclusao.TipoTitular;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
class RepositorioPedidosExclusaoJdbc implements RepositorioPedidosExclusao {

    private final JdbcTemplate jdbc;

    RepositorioPedidosExclusaoJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean existeEmAndamento(UUID titularId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM pedido_titular
                                WHERE titular_id = ? AND tipo = 'ELIMINACAO' AND status IN ('RECEBIDO', 'EM_ANDAMENTO'))
                """, Boolean.class, titularId));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void salvar(PedidoExclusao pedido) {
        jdbc.update("""
                INSERT INTO pedido_titular (id, titular_sub, tipo, status, titular_id, tipo_titular, solicitante_sub,
                                            email_comprovante, criado_em, prazo_em)
                VALUES (?, ?, 'ELIMINACAO', 'EM_ANDAMENTO', ?, ?, ?, ?, ?, ?)
                """, pedido.protocolo(), pedido.titularSub(), pedido.titularId(), pedido.tipoTitular().name(),
                pedido.solicitanteSub(), pedido.emailComprovante(), Timestamp.from(pedido.solicitadoEm()),
                Timestamp.from(pedido.prazoEm()));
    }

    @Override
    public Optional<PedidoExclusao> buscar(UUID protocolo) {
        List<PedidoExclusao> pedidos = jdbc.query("""
                SELECT id, titular_id, tipo_titular, titular_sub, solicitante_sub, email_comprovante, criado_em, prazo_em
                  FROM pedido_titular WHERE id = ? AND tipo = 'ELIMINACAO'
                """, (rs, i) -> new PedidoExclusao(
                rs.getObject("id", UUID.class),
                rs.getObject("titular_id", UUID.class),
                TipoTitular.valueOf(rs.getString("tipo_titular")),
                rs.getString("titular_sub"),
                rs.getString("solicitante_sub"),
                rs.getString("email_comprovante"),
                rs.getTimestamp("criado_em").toInstant(),
                rs.getTimestamp("prazo_em").toInstant()), protocolo);
        return pedidos.stream().findFirst();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrarConfirmacao(UUID protocolo, ServicoConfirmador servico, Instant confirmadoEm) {
        jdbc.update("""
                INSERT INTO confirmacao_exclusao (pedido_id, servico, confirmado_em) VALUES (?, ?, ?)
                ON CONFLICT (pedido_id, servico) DO NOTHING
                """, protocolo, servico.nome(), Timestamp.from(confirmadoEm));
    }

    @Override
    public Set<ServicoConfirmador> servicosConfirmados(UUID protocolo) {
        Set<ServicoConfirmador> confirmados = EnumSet.noneOf(ServicoConfirmador.class);
        jdbc.queryForList("SELECT servico FROM confirmacao_exclusao WHERE pedido_id = ?", String.class, protocolo)
                .forEach(nome -> ServicoConfirmador.porNome(nome).ifPresent(confirmados::add));
        return confirmados;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean concluir(UUID protocolo, Instant concluidoEm) {
        return jdbc.update("""
                UPDATE pedido_titular
                   SET status = 'CONCLUIDO', concluido_em = ?, comprovante_enviado_em = ?, email_comprovante = NULL
                 WHERE id = ? AND status <> 'CONCLUIDO'
                """, Timestamp.from(concluidoEm), Timestamp.from(concluidoEm), protocolo) == 1;
    }
}
