-- Pedido de exclusão do titular (HU-022): prazo, confirmação de cada serviço e projeções de apoio.

ALTER TABLE pedido_titular
    ADD COLUMN titular_id          UUID,
    ADD COLUMN tipo_titular        VARCHAR(20),
    ADD COLUMN solicitante_sub     VARCHAR(255),
    ADD COLUMN email_comprovante   VARCHAR(320),
    ADD COLUMN prazo_em            TIMESTAMPTZ,
    ADD COLUMN comprovante_enviado_em TIMESTAMPTZ,
    ADD CONSTRAINT ck_pedido_titular_tipo_titular CHECK (tipo_titular IS NULL OR tipo_titular IN ('IDOSO', 'FAMILIAR'));

COMMENT ON COLUMN pedido_titular.id IS 'Protocolo do pedido';
COMMENT ON COLUMN pedido_titular.titular_id IS 'Id do perfil do titular dos dados no identity-service';
COMMENT ON COLUMN pedido_titular.solicitante_sub IS 'Claim "sub" de quem fez o pedido (o titular ou um Familiar Administrador)';
COMMENT ON COLUMN pedido_titular.email_comprovante IS 'Destino do comprovante; apagado quando o comprovante é enviado';

CREATE INDEX ix_pedido_titular_titular_id ON pedido_titular (titular_id);

-- Só um pedido de eliminação em andamento por titular.
CREATE UNIQUE INDEX uk_pedido_titular_eliminacao_em_andamento ON pedido_titular (titular_id)
    WHERE tipo = 'ELIMINACAO' AND status IN ('RECEBIDO', 'EM_ANDAMENTO');

CREATE TABLE confirmacao_exclusao (
    pedido_id     UUID         NOT NULL,
    servico       VARCHAR(50)  NOT NULL,
    confirmado_em TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_confirmacao_exclusao PRIMARY KEY (pedido_id, servico),
    CONSTRAINT fk_confirmacao_exclusao_pedido FOREIGN KEY (pedido_id) REFERENCES pedido_titular (id) ON DELETE CASCADE
);

COMMENT ON TABLE confirmacao_exclusao IS 'Confirmações lgpd.erasure.completed recebidas, uma por serviço e pedido';

-- Perfis projetados de user.created: ligam o titularId ao sub do token e guardam o e-mail do comprovante.
CREATE TABLE projecao_perfil (
    perfil_id     UUID         NOT NULL,
    keycloak_sub  VARCHAR(255) NOT NULL,
    tipo          VARCHAR(20)  NOT NULL,
    nome          VARCHAR(150) NOT NULL,
    email         VARCHAR(320),
    atualizado_em TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_projecao_perfil PRIMARY KEY (perfil_id),
    CONSTRAINT ck_projecao_perfil_tipo CHECK (tipo IN ('IDOSO', 'FAMILIAR'))
);

CREATE UNIQUE INDEX uk_projecao_perfil_sub ON projecao_perfil (keycloak_sub);

-- Assinatura do familiar, projetada de subscription.changed: assinatura Premium ativa bloqueia a exclusão.
CREATE TABLE projecao_assinatura (
    familiar_sub  VARCHAR(255) NOT NULL,
    plano         VARCHAR(20)  NOT NULL,
    ativa         BOOLEAN      NOT NULL,
    atualizado_em TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_projecao_assinatura PRIMARY KEY (familiar_sub)
);

-- Familiares administradores de cada idoso. Ainda sem evento de origem: o care-service não publica o vínculo.
CREATE TABLE projecao_vinculo_administrador (
    idoso_id     UUID         NOT NULL,
    familiar_sub VARCHAR(255) NOT NULL,
    CONSTRAINT pk_projecao_vinculo_administrador PRIMARY KEY (idoso_id, familiar_sub)
);
