-- Id do idoso no care-service, vindo de user.created: é a chave dos dados do idoso nos demais serviços.

ALTER TABLE projecao_perfil ADD COLUMN idoso_id UUID NULL;

COMMENT ON COLUMN projecao_perfil.idoso_id IS 'Id do idoso no care-service; nulo para familiar e para idoso sem cadastro no care';
