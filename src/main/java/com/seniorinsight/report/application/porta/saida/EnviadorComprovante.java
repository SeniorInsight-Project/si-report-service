package com.seniorinsight.report.application.porta.saida;

import java.time.Instant;
import java.util.UUID;

/** Envia por e-mail o comprovante de que a exclusão foi concluída. */
public interface EnviadorComprovante {

    /** O comprovante só informa o protocolo e as datas: nenhum dado do titular além do destino. */
    void enviar(String email, UUID protocolo, Instant solicitadoEm, Instant concluidoEm);
}
