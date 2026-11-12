package com.seniorinsight.report.adapters.out.persistencia;

import java.sql.Timestamp;

import com.seniorinsight.report.application.porta.saida.RepositorioProjecoes;
import com.seniorinsight.report.domain.projecao.AssinaturaFamiliar;
import com.seniorinsight.report.domain.projecao.OcorrenciaSos;
import com.seniorinsight.report.domain.projecao.PerfilTitular;
import com.seniorinsight.report.domain.projecao.ScoreIdoso;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class RepositorioProjecoesJdbc implements RepositorioProjecoes {

    private final JdbcTemplate jdbc;

    RepositorioProjecoesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void salvarScore(ScoreIdoso score) {
        jdbc.update("""
                INSERT INTO projecao_score (idoso_id, score, calculado_em, atualizado_em)
                VALUES (?, ?, ?, now())
                ON CONFLICT (idoso_id) DO UPDATE
                   SET score = EXCLUDED.score, calculado_em = EXCLUDED.calculado_em, atualizado_em = now()
                 WHERE projecao_score.calculado_em <= EXCLUDED.calculado_em
                """, score.idosoId(), score.score(), Timestamp.from(score.calculadoEm()));
    }

    @Override
    public void salvarSos(OcorrenciaSos sos) {
        jdbc.update("""
                INSERT INTO projecao_sos (sos_id, idoso_id, status, ocorrido_em, atualizado_em)
                VALUES (?, ?, ?, ?, now())
                ON CONFLICT (sos_id) DO UPDATE
                   SET status = EXCLUDED.status, atualizado_em = now()
                """, sos.sosId(), sos.idosoId(), sos.status(), Timestamp.from(sos.ocorridoEm()));
    }

    @Override
    public void salvarPerfil(PerfilTitular perfil) {
        jdbc.update("""
                INSERT INTO projecao_perfil (perfil_id, keycloak_sub, tipo, nome, email, idoso_id, atualizado_em)
                VALUES (?, ?, ?, ?, ?, ?, now())
                ON CONFLICT (perfil_id) DO UPDATE
                   SET keycloak_sub = EXCLUDED.keycloak_sub, tipo = EXCLUDED.tipo, nome = EXCLUDED.nome,
                       email = EXCLUDED.email, idoso_id = EXCLUDED.idoso_id, atualizado_em = now()
                """, perfil.perfilId(), perfil.keycloakSub(), perfil.tipo().name(), perfil.nome(), perfil.email(),
                perfil.idosoId());
    }

    @Override
    public void salvarAssinatura(AssinaturaFamiliar assinatura) {
        jdbc.update("""
                INSERT INTO projecao_assinatura (familiar_sub, plano, ativa, atualizado_em)
                VALUES (?, ?, ?, now())
                ON CONFLICT (familiar_sub) DO UPDATE
                   SET plano = EXCLUDED.plano, ativa = EXCLUDED.ativa, atualizado_em = now()
                """, assinatura.familiarSub(), assinatura.plano(), assinatura.ativa());
    }
}
