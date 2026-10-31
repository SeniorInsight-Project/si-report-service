package com.seniorinsight.report.domain.projecao;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Estado de uma ocorrência de SOS, como chega do serviço de SOS. */
public record OcorrenciaSos(UUID sosId, UUID idosoId, String status, Instant ocorridoEm) {

    public OcorrenciaSos {
        Objects.requireNonNull(sosId, "sosId");
        Objects.requireNonNull(idosoId, "idosoId");
        Objects.requireNonNull(ocorridoEm, "ocorridoEm");
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("status é obrigatório");
        }
    }
}
