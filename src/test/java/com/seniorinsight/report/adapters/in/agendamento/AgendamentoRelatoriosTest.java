package com.seniorinsight.report.adapters.in.agendamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;

import com.seniorinsight.report.application.relatorio.GerarRelatoriosSemanais;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AgendamentoRelatoriosTest {

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withBean(GerarRelatoriosSemanais.class, () -> mock(GerarRelatoriosSemanais.class))
            .withUserConfiguration(AgendamentoRelatorios.class)
            .withPropertyValues("report.agendamento.zona=America/Sao_Paulo");

    @Test
    void ligadoPorPropriedade() {
        contexto.withPropertyValues("report.agendamento.habilitado=true")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(AgendamentoRelatorios.class);
                    ctx.getBean(AgendamentoRelatorios.class).gerarSemanais();
                    verify(ctx.getBean(GerarRelatoriosSemanais.class)).executar(any(LocalDate.class));
                });
    }

    @Test
    void desligadoPorPropriedade() {
        contexto.withPropertyValues("report.agendamento.habilitado=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(AgendamentoRelatorios.class));
    }
}
