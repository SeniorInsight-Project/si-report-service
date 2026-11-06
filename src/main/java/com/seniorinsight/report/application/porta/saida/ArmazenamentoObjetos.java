package com.seniorinsight.report.application.porta.saida;

import java.net.URI;
import java.time.Duration;

/**
 * Armazenamento de arquivos gerados (PDFs dos relatórios, exportações LGPD) em um bucket de objetos.
 *
 * <p>Implementações: MinIO (API S3) no ambiente local; Cloud Storage em produção.
 */
public interface ArmazenamentoObjetos {

    /** Validade padrão das URLs assinadas entregues ao usuário. Também é o máximo aceito pela API S3. */
    Duration VALIDADE_URL_ASSINADA = Duration.ofDays(7);

    void salvar(String chave, byte[] conteudo, String contentType);

    /** URL de download sem autenticação, válida por {@code validade} (no máximo 7 dias). */
    URI gerarUrlAssinada(String chave, Duration validade);

    default URI gerarUrlAssinada(String chave) {
        return gerarUrlAssinada(chave, VALIDADE_URL_ASSINADA);
    }
}
