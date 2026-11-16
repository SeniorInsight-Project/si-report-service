package com.seniorinsight.report.adapters.out.persistencia;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.seniorinsight.report.application.porta.saida.ConsultaTitulares;
import com.seniorinsight.report.domain.exclusao.TipoTitular;
import com.seniorinsight.report.domain.projecao.AssinaturaFamiliar;
import com.seniorinsight.report.domain.projecao.PerfilTitular;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
class ConsultaTitularesJdbc implements ConsultaTitulares {

    private static final String SELECT_PERFIL =
            "SELECT perfil_id, keycloak_sub, tipo, nome, email, idoso_id FROM projecao_perfil";

    private static final RowMapper<PerfilTitular> PERFIL = (rs, i) -> new PerfilTitular(
            rs.getObject("perfil_id", UUID.class),
            rs.getString("keycloak_sub"),
            TipoTitular.valueOf(rs.getString("tipo")),
            rs.getString("nome"),
            rs.getString("email"),
            rs.getObject("idoso_id", UUID.class));

    private final JdbcTemplate jdbc;

    ConsultaTitularesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<PerfilTitular> buscarPerfilPorSub(String keycloakSub) {
        return jdbc.query(SELECT_PERFIL + " WHERE keycloak_sub = ?", PERFIL, keycloakSub).stream().findFirst();
    }

    @Override
    public Optional<AssinaturaFamiliar> buscarAssinatura(String familiarSub) {
        List<AssinaturaFamiliar> assinaturas = jdbc.query(
                "SELECT familiar_sub, plano, ativa FROM projecao_assinatura WHERE familiar_sub = ?",
                (rs, i) -> new AssinaturaFamiliar(rs.getString("familiar_sub"), rs.getString("plano"),
                        rs.getBoolean("ativa")), familiarSub);
        return assinaturas.stream().findFirst();
    }
}
