package com.seniorinsight.report.application.relatorio;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import com.seniorinsight.report.application.relatorio.GerarRelatoriosSemanais.PeriodoRelatorio;
import org.junit.jupiter.api.Test;

class GerarRelatoriosSemanaisTest {

    private final GerarRelatoriosSemanais caso = new GerarRelatoriosSemanais();

    @Test
    void periodoEhASemanaFechadaAnterior() {
        // 2026-11-02 é segunda-feira.
        PeriodoRelatorio esperado = new PeriodoRelatorio(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 1));

        assertThat(caso.executar(LocalDate.of(2026, 11, 2))).isEqualTo(esperado);
        assertThat(caso.executar(LocalDate.of(2026, 11, 8))).isEqualTo(esperado);
    }
}
