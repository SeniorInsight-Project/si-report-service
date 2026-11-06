package com.seniorinsight.report.adapters.in.agendamento;

import java.time.LocalDate;
import java.time.ZoneId;

import com.seniorinsight.report.application.relatorio.GerarRelatoriosSemanais;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Dispara a geração dos relatórios semanais pelo Spring Scheduler. Usado no ambiente local; em produção o gatilho é
 * o Cloud Scheduler e este agendamento fica desligado ({@code report.agendamento.habilitado=false}).
 */
@Component
@ConditionalOnProperty(name = "report.agendamento.habilitado", havingValue = "true")
class AgendamentoRelatorios {

    private final GerarRelatoriosSemanais gerarRelatoriosSemanais;
    private final ZoneId zona;

    AgendamentoRelatorios(GerarRelatoriosSemanais gerarRelatoriosSemanais,
                          @Value("${report.agendamento.zona}") ZoneId zona) {
        this.gerarRelatoriosSemanais = gerarRelatoriosSemanais;
        this.zona = zona;
    }

    @Scheduled(cron = "${report.agendamento.cron}", zone = "${report.agendamento.zona}")
    void gerarSemanais() {
        gerarRelatoriosSemanais.executar(LocalDate.now(zona));
    }
}
