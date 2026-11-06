-- Estrutura inicial do report-service. Roda no schema "report" (spring.flyway.default-schema).

CREATE TABLE relatorio (
    id             UUID         NOT NULL DEFAULT gen_random_uuid(),
    idoso_id       UUID         NOT NULL,
    tipo           VARCHAR(30)  NOT NULL,
    periodo_inicio DATE         NOT NULL,
    periodo_fim    DATE         NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDENTE',
    objeto_chave   VARCHAR(500),
    gerado_em      TIMESTAMPTZ,
    criado_em      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_relatorio PRIMARY KEY (id),
    CONSTRAINT uk_relatorio_idoso_tipo_periodo UNIQUE (idoso_id, tipo, periodo_inicio, periodo_fim),
    CONSTRAINT ck_relatorio_tipo CHECK (tipo IN ('SEMANAL_MEDICO', 'CARTEIRA_OPERADORA')),
    CONSTRAINT ck_relatorio_status CHECK (status IN ('PENDENTE', 'GERANDO', 'PRONTO', 'FALHOU')),
    CONSTRAINT ck_relatorio_periodo CHECK (periodo_fim >= periodo_inicio)
);

COMMENT ON COLUMN relatorio.objeto_chave IS 'Chave do PDF no armazenamento de objetos (MinIO local, Cloud Storage em produção)';

CREATE TABLE pedido_titular (
    id           UUID         NOT NULL DEFAULT gen_random_uuid(),
    titular_sub  VARCHAR(255) NOT NULL,
    tipo         VARCHAR(20)  NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'RECEBIDO',
    objeto_chave VARCHAR(500),
    criado_em    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    concluido_em TIMESTAMPTZ,
    CONSTRAINT pk_pedido_titular PRIMARY KEY (id),
    CONSTRAINT ck_pedido_titular_tipo CHECK (tipo IN ('EXPORTACAO', 'ELIMINACAO')),
    CONSTRAINT ck_pedido_titular_status CHECK (status IN ('RECEBIDO', 'EM_ANDAMENTO', 'CONCLUIDO', 'FALHOU'))
);

COMMENT ON TABLE pedido_titular IS 'Pedidos do titular de dados (LGPD): exportação e eliminação';
COMMENT ON COLUMN pedido_titular.titular_sub IS 'Claim "sub" do titular no Keycloak';

CREATE INDEX ix_pedido_titular_titular_sub ON pedido_titular (titular_sub);

-- Projeções alimentadas pelos eventos recebidos na fila report.q.

CREATE TABLE projecao_score (
    idoso_id      UUID          NOT NULL,
    score         NUMERIC(5, 2) NOT NULL,
    calculado_em  TIMESTAMPTZ   NOT NULL,
    atualizado_em TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_projecao_score PRIMARY KEY (idoso_id)
);

COMMENT ON TABLE projecao_score IS 'Último score de cada idoso, projetado dos eventos score.*';

CREATE TABLE projecao_sos (
    sos_id        UUID        NOT NULL,
    idoso_id      UUID        NOT NULL,
    status        VARCHAR(30) NOT NULL,
    ocorrido_em   TIMESTAMPTZ NOT NULL,
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_projecao_sos PRIMARY KEY (sos_id)
);

COMMENT ON TABLE projecao_sos IS 'Ocorrências de SOS, projetadas dos eventos sos.*';

CREATE INDEX ix_projecao_sos_idoso_ocorrido ON projecao_sos (idoso_id, ocorrido_em);

-- Idempotência do consumidor: um evento (message_id) só é aplicado uma vez.
CREATE TABLE evento_processado (
    evento_id     UUID         NOT NULL,
    tipo_evento   VARCHAR(150) NOT NULL,
    processado_em TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_evento_processado PRIMARY KEY (evento_id)
);

CREATE TABLE outbox_evento (
    id            UUID         NOT NULL DEFAULT gen_random_uuid(),
    tipo_agregado VARCHAR(100) NOT NULL,
    agregado_id   UUID         NOT NULL,
    tipo_evento   VARCHAR(150) NOT NULL,
    payload       JSONB        NOT NULL,
    criado_em     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    publicado_em  TIMESTAMPTZ,
    tentativas    INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT pk_outbox_evento PRIMARY KEY (id)
);

COMMENT ON COLUMN outbox_evento.tipo_evento IS 'Routing key na exchange si.events, ex.: report.ready';

-- O relay só lê pendentes, em ordem de criação.
CREATE INDEX ix_outbox_evento_pendentes ON outbox_evento (criado_em) WHERE publicado_em IS NULL;
