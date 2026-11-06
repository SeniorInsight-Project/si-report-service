package com.seniorinsight.report.adapters.out.armazenamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.seniorinsight.report.TesteIntegracao;
import com.seniorinsight.report.application.porta.saida.ArmazenamentoObjetos;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@TesteIntegracao
class ArmazenamentoMinioIntegracaoTest {

    @Autowired
    ArmazenamentoObjetos armazenamento;

    @Test
    void gravaOObjetoEEntregaUrlAssinadaDeSeteDias() throws Exception {
        armazenamento.salvar("relatorios/teste.pdf", "%PDF-teste".getBytes(StandardCharsets.UTF_8),
                "application/pdf");

        URI url = armazenamento.gerarUrlAssinada("relatorios/teste.pdf");

        assertThat(url.getQuery()).contains("X-Amz-Expires=604800");
        HttpResponse<String> resposta = HttpClient.newHttpClient()
                .send(HttpRequest.newBuilder(url).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat(resposta.body()).isEqualTo("%PDF-teste");
        assertThat(resposta.headers().firstValue("Content-Type")).hasValue("application/pdf");
    }

    @Test
    void recusaValidadeAcimaDeSeteDias() {
        assertThatThrownBy(() -> armazenamento.gerarUrlAssinada("qualquer.pdf", Duration.ofDays(8)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
