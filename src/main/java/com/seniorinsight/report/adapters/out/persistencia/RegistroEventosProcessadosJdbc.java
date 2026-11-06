package com.seniorinsight.report.adapters.out.persistencia;

import java.util.UUID;

import com.seniorinsight.report.application.porta.saida.RegistroEventosProcessados;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
class RegistroEventosProcessadosJdbc implements RegistroEventosProcessados {

    private final JdbcTemplate jdbc;

    RegistroEventosProcessadosJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Exige transação: o registro só vale se a projeção for gravada junto. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean registrarSeNovo(UUID eventoId, String tipoEvento) {
        return jdbc.update("""
                INSERT INTO evento_processado (evento_id, tipo_evento) VALUES (?, ?)
                ON CONFLICT (evento_id) DO NOTHING
                """, eventoId, tipoEvento) == 1;
    }
}
