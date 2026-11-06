package com.seniorinsight.report.application.projecao;

import java.util.UUID;

import com.seniorinsight.report.application.porta.saida.RegistroEventosProcessados;
import com.seniorinsight.report.application.porta.saida.RepositorioProjecoes;
import com.seniorinsight.report.domain.projecao.OcorrenciaSos;
import com.seniorinsight.report.domain.projecao.ScoreIdoso;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aplica nas projeções os eventos de Scores e de SOS. O registro do evento e a atualização da projeção estão na
 * mesma transação: um evento repetido (mesmo id) não é aplicado de novo.
 */
@Service
public class AtualizarProjecoes {

    private final RegistroEventosProcessados eventosProcessados;
    private final RepositorioProjecoes projecoes;

    public AtualizarProjecoes(RegistroEventosProcessados eventosProcessados, RepositorioProjecoes projecoes) {
        this.eventosProcessados = eventosProcessados;
        this.projecoes = projecoes;
    }

    /** @return {@code false} se o evento já tinha sido aplicado */
    @Transactional
    public boolean aplicarScore(UUID eventoId, String tipoEvento, ScoreIdoso score) {
        if (!eventosProcessados.registrarSeNovo(eventoId, tipoEvento)) {
            return false;
        }
        projecoes.salvarScore(score);
        return true;
    }

    /** @return {@code false} se o evento já tinha sido aplicado */
    @Transactional
    public boolean aplicarSos(UUID eventoId, String tipoEvento, OcorrenciaSos sos) {
        if (!eventosProcessados.registrarSeNovo(eventoId, tipoEvento)) {
            return false;
        }
        projecoes.salvarSos(sos);
        return true;
    }
}
