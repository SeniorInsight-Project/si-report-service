package com.seniorinsight.report.application.relatorio;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Gera os relatórios semanais (HU-046) da semana anterior à data de referência.
 *
 * <p>Esqueleto: por ora só calcula o período. A montagem do PDF, o upload e o evento {@code report.ready} chegam
 * nas próximas issues.
 */
@Service
public class GerarRelatoriosSemanais {

    private static final Logger log = LoggerFactory.getLogger(GerarRelatoriosSemanais.class);

    public PeriodoRelatorio executar(LocalDate referencia) {
        PeriodoRelatorio periodo = PeriodoRelatorio.semanaAnterior(referencia);
        log.info("Relatórios semanais: período {} a {}", periodo.inicio(), periodo.fim());
        return periodo;
    }

    /** Semana fechada, de segunda a domingo. */
    public record PeriodoRelatorio(LocalDate inicio, LocalDate fim) {

        static PeriodoRelatorio semanaAnterior(LocalDate referencia) {
            LocalDate inicio = referencia.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1);
            return new PeriodoRelatorio(inicio, inicio.plusDays(6));
        }
    }
}
