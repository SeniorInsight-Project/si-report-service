package com.seniorinsight.report.domain.projecao;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.seniorinsight.report.domain.exclusao.TipoTitular;
import org.junit.jupiter.api.Test;

class PerfilTitularTest {

    private final UUID perfilId = UUID.randomUUID();
    private final UUID idosoId = UUID.randomUUID();

    @Test
    void idosoComIdosoIdTemOIdosoIdComoChaveDeDominio() {
        PerfilTitular idoso = new PerfilTitular(perfilId, "sub", TipoTitular.IDOSO, "Maria", null, idosoId);

        assertThat(idoso.chaveDeDominio()).isEqualTo(idosoId);
    }

    @Test
    void idosoSemIdosoIdTemOIdDoPerfilComoChaveDeDominio() {
        PerfilTitular idoso = new PerfilTitular(perfilId, "sub", TipoTitular.IDOSO, "Maria", null, null);

        assertThat(idoso.chaveDeDominio()).isEqualTo(perfilId);
    }

    @Test
    void familiarTemOIdDoPerfilComoChaveDeDominio() {
        PerfilTitular familiar = new PerfilTitular(perfilId, "sub", TipoTitular.FAMILIAR, "Ana", "ana@email.com", null);

        assertThat(familiar.chaveDeDominio()).isEqualTo(perfilId);
    }
}
