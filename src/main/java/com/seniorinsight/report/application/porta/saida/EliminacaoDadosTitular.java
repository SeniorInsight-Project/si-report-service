package com.seniorinsight.report.application.porta.saida;

import java.util.List;
import java.util.UUID;

/** Apaga do banco do report-service tudo o que é do titular (relatórios, exportações e projeções). */
public interface EliminacaoDadosTitular {

    /**
     * @return as chaves dos arquivos do titular no armazenamento de objetos, para o chamador apagá-los
     */
    List<String> apagarRegistros(UUID titularId, String titularSub);
}
