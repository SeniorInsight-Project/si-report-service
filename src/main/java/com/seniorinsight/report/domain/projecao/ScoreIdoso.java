package com.seniorinsight.report.domain.projecao;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Último score calculado para um idoso, como chega do serviço de Scores. */
public record ScoreIdoso(UUID idosoId, BigDecimal score, Instant calculadoEm) {

    public ScoreIdoso {
        Objects.requireNonNull(idosoId, "idosoId");
        Objects.requireNonNull(score, "score");
        Objects.requireNonNull(calculadoEm, "calculadoEm");
    }
}
