package com.seniorinsight.report.application.porta.saida;

import com.seniorinsight.report.domain.projecao.OcorrenciaSos;
import com.seniorinsight.report.domain.projecao.ScoreIdoso;

/** Projeções locais dos dados de outros serviços, usadas na montagem dos relatórios. */
public interface RepositorioProjecoes {

    /** Grava o score do idoso; um score calculado antes do que já está gravado é ignorado. */
    void salvarScore(ScoreIdoso score);

    void salvarSos(OcorrenciaSos sos);
}
