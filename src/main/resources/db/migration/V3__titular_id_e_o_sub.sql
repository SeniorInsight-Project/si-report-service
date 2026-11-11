-- O titular_id do pedido passa a ser o sub do Keycloak do titular (contrato report-v1), e não mais o id do perfil.

COMMENT ON COLUMN pedido_titular.titular_id IS 'Sub do Keycloak do titular, como UUID';
COMMENT ON COLUMN pedido_titular.solicitante_sub IS 'Claim "sub" de quem fez o pedido, sempre o próprio titular';

UPDATE pedido_titular
   SET titular_id = titular_sub::uuid
 WHERE tipo = 'ELIMINACAO'
   AND titular_sub ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$';
