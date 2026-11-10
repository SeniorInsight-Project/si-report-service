package com.seniorinsight.report.adapters.out.persistencia;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.seniorinsight.report.application.porta.saida.EliminacaoDadosTitular;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Apaga os dados do titular no banco do report-service. O pedido de eliminação em si permanece: ele é o registro do
 * atendimento ao direito do titular (protocolo, datas e confirmações), sem relatórios nem projeções.
 */
@Repository
class EliminacaoDadosTitularJdbc implements EliminacaoDadosTitular {

    private final JdbcTemplate jdbc;

    EliminacaoDadosTitularJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<String> apagarRegistros(UUID titularId, String titularSub) {
        List<String> arquivos = new ArrayList<>(jdbc.queryForList("""
                DELETE FROM relatorio WHERE idoso_id = ? RETURNING objeto_chave
                """, String.class, titularId));
        arquivos.addAll(jdbc.queryForList("""
                DELETE FROM pedido_titular
                 WHERE tipo = 'EXPORTACAO' AND (titular_id = ? OR titular_sub = ?)
                RETURNING objeto_chave
                """, String.class, titularId, titularSub));

        jdbc.update("DELETE FROM projecao_score WHERE idoso_id = ?", titularId);
        jdbc.update("DELETE FROM projecao_sos WHERE idoso_id = ?", titularId);
        jdbc.update("DELETE FROM projecao_vinculo_administrador WHERE idoso_id = ? OR familiar_sub = ?",
                titularId, titularSub);
        jdbc.update("DELETE FROM projecao_assinatura WHERE familiar_sub = ?", titularSub);
        jdbc.update("DELETE FROM projecao_perfil WHERE perfil_id = ?", titularId);

        // RETURNING devolve null quando a linha ainda não tinha arquivo.
        arquivos.removeIf(chave -> chave == null);
        return arquivos;
    }
}
