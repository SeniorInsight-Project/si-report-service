# si-report-service

Serviço de relatórios: relatório semanal em PDF para o médico (HU-046), carteira da operadora sem nomes e exportação de dados do titular (LGPD). Publica `report.ready` e `lgpd.erasure.requested`.

Parte do SeniorInsight — ver o Documento de Arquitetura de Software (seção 6) para as convenções.

Branches: `main` (produção) e `develop` (testes, branch padrão). Trabalhe na branch criada pelo card e abra PR para `develop` — ver [CONTRIBUTING.md](CONTRIBUTING.md).

## Stack

Java 21, Spring Boot 3.5, PostgreSQL (schema `report`, migrações com Flyway), RabbitMQ (exchange topic `si.events`, fila `report.q`), armazenamento de objetos (MinIO local, Cloud Storage em produção) e Keycloak como emissor dos tokens (o serviço é só resource server).

## Organização do código

Pacote base `com.seniorinsight.report`, em arquitetura hexagonal (mesmo padrão do si-identity-service):

| Pacote | O que tem | Pode depender de |
|---|---|---|
| `domain` | Modelo e eventos de domínio, Java puro | nada (nem Spring, nem JPA) |
| `application` | Casos de uso e portas (`porta.saida.PublicadorEventos`, `ArmazenamentoObjetos`, `RepositorioProjecoes`, `RegistroEventosProcessados`) | `domain` |
| `adapters.in` | Entrada: HTTP e segurança, consumidor da `report.q`, agendamento | `application`, `domain` |
| `adapters.out` | Saída: banco, outbox, MinIO | `application`, `domain` |

`adapters.in` e `adapters.out` não se conhecem. As regras são verificadas pelo `ArquiteturaTest` (ArchUnit) a cada build.

## Banco (schema `report`)

| Tabela | Uso |
|---|---|
| `relatorio` | Relatórios gerados (semanal do médico, carteira da operadora), com a chave do PDF no armazenamento |
| `pedido_titular` | Pedidos LGPD do titular (exportação, eliminação) |
| `projecao_score` | Último score de cada idoso, projetado dos eventos `score.*` |
| `projecao_sos` | Ocorrências de SOS, projetadas dos eventos `sos.*` |
| `evento_processado` | Ids dos eventos já consumidos (idempotência) |
| `outbox_evento` | Eventos a publicar em `si.events` |

## Rodando localmente

Pré-requisitos: JDK 21, Maven 3.9+ e Docker.

### Sem infraestrutura própria (Testcontainers)

Sobe o serviço com PostgreSQL, RabbitMQ e MinIO em containers descartáveis:

```bash
KEYCLOAK_ISSUER_URI=http://localhost:8180/realms/seniorinsight mvn spring-boot:test-run
```

Sem um Keycloak no ar o serviço sobe normalmente: o health e o Swagger funcionam, e as rotas protegidas respondem 401.

### Com PostgreSQL, RabbitMQ, MinIO e Keycloak já rodando

```bash
cp .env.example .env   # preencha as senhas
set -a && source .env && set +a
mvn spring-boot:run
```

O usuário do banco precisa poder criar o schema `report` (ou o schema precisa existir com ele como dono): o Flyway cria o schema e aplica as migrações na subida.

### Endpoints

| Rota | Acesso |
|---|---|
| `GET /actuator/health` (e `/liveness`, `/readiness`) | público |
| `/swagger-ui.html` e `/v3/api-docs` | público |
| todo o resto | token do Keycloak |

## Variáveis de ambiente

O `application.yml` não tem segredos; tudo vem do ambiente. O modelo está no [`.env.example`](.env.example).

| Variável | Padrão | Descrição |
|---|---|---|
| `DB_HOST` | `localhost` | Host do PostgreSQL |
| `DB_PORT` | `5432` | Porta do PostgreSQL |
| `DB_NAME` | `seniorinsight` | Banco (cada serviço usa o seu schema) |
| `DB_USERNAME` | — | Usuário do banco |
| `DB_PASSWORD` | — | Senha do banco |
| `RABBITMQ_HOST` | `localhost` | Host do RabbitMQ |
| `RABBITMQ_PORT` | `5672` | Porta AMQP |
| `RABBITMQ_VHOST` | `/` | Virtual host |
| `RABBITMQ_USERNAME` | — | Usuário do RabbitMQ |
| `RABBITMQ_PASSWORD` | — | Senha do RabbitMQ |
| `KEYCLOAK_ISSUER_URI` | — | Issuer do realm, ex.: `http://localhost:8180/realms/seniorinsight` |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWKSETURI` | — | Opcional: URL das chaves públicas quando o issuer não é acessível pelo serviço (ex.: no Compose) |
| `ARMAZENAMENTO_PROVEDOR` | `minio` | Adaptador de armazenamento de objetos |
| `ARMAZENAMENTO_BUCKET` | `si-reports` | Bucket dos PDFs e exportações (criado no primeiro upload) |
| `MINIO_ENDPOINT` | `http://localhost:9000` | API S3 do MinIO, como o serviço a alcança |
| `MINIO_PUBLIC_ENDPOINT` | `MINIO_ENDPOINT` | Endereço usado nas URLs assinadas (precisa ser alcançável pelo navegador) |
| `MINIO_REGION` | `us-east-1` | Região usada na assinatura |
| `MINIO_ACCESS_KEY` | — | Access key do MinIO |
| `MINIO_SECRET_KEY` | — | Secret key do MinIO |
| `REPORT_AGENDAMENTO_HABILITADO` | `true` | Liga o Spring Scheduler dos relatórios (desligar em produção) |
| `SERVER_PORT` | `8080` | Porta HTTP |

`KEYCLOAK_ISSUER_URI` precisa ser **exatamente** o valor do claim `iss` dos tokens. Quando o issuer não é acessível de dentro do container (no Compose ele é `http://localhost:8180/...`), informe `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWKSETURI` com o endereço interno do Keycloak: as chaves vêm de lá e o `iss` continua sendo validado.

Ajustes opcionais (valores padrão no `application.yml`): `REPORT_OUTBOX_RELAY_INTERVALO` (`PT1S`), `REPORT_OUTBOX_RELAY_TAMANHO_LOTE` (`100`), `REPORT_OUTBOX_RELAY_TIMEOUT_CONFIRMACAO` (`PT5S`), `REPORT_MENSAGERIA_ROUTING_KEYS` (`score.#,sos.#`), `REPORT_AGENDAMENTO_CRON` (`0 0 6 * * MON`) e `REPORT_AGENDAMENTO_ZONA` (`America/Sao_Paulo`).

## Segurança

O serviço não tem login nem emite tokens: valida o JWT do Keycloak, sem sessão (stateless). As roles de `realm_access.roles` viram authorities `ROLE_<role>` e os scopes do token viram `SCOPE_<scope>`.

## Eventos

### Publicação (outbox)

Quem precisa publicar um evento chama a porta `PublicadorEventos` dentro da transação do caso de uso. O evento é gravado em `outbox_evento` junto com a mudança de estado, e um job agendado (`RelayOutbox`) publica os pendentes na exchange `si.events` com routing key = tipo do evento (ex.: `report.ready`), `message_id` = id da linha na outbox e publisher confirms. `FOR UPDATE SKIP LOCKED` permite várias réplicas rodando o relay. A entrega é "pelo menos uma vez".

### Consumo (`report.q`)

O serviço declara a fila `report.q`, ligada a `si.events` pelas routing keys `score.#` e `sos.#`, com dead-letter para `report.q.dlq` (exchange `report.dlx`).

- **Ack manual**: a mensagem só é confirmada depois que a projeção foi gravada.
- **Idempotência**: o id do evento é o `message_id`; ele é gravado em `evento_processado` na mesma transação da projeção, e um evento repetido é confirmado sem efeito.
- **Dead-letter**: mensagem sem `message_id`, com payload inválido ou que falhou ao gravar recebe nack sem requeue e vai para `report.q.dlq`.

Payloads esperados (campos do record do evento no serviço de origem):

| Tipo | Campos |
|---|---|
| `score.*` | `idosoId`, `score`, `calculadoEm` |
| `sos.*` | `sosId`, `idosoId`, `status`, `ocorridoEm` |

## Armazenamento de objetos

A porta `ArmazenamentoObjetos` grava arquivos e gera URLs assinadas de download, válidas por 7 dias (`VALIDADE_URL_ASSINADA`, também o máximo aceito). O adaptador `ArmazenamentoMinio` (API S3) é ligado com `ARMAZENAMENTO_PROVEDOR=minio`; o de Cloud Storage para produção entra como outro adaptador da mesma porta.

As URLs são assinadas com `MINIO_PUBLIC_ENDPOINT`, porque o host interno (`minio:9000` no Compose) não é alcançável pelo navegador.

## Agendamento

`AgendamentoRelatorios` dispara a geração dos relatórios semanais (segunda às 6h, horário de Brasília) pelo Spring Scheduler. Em produção o gatilho é o Cloud Scheduler: desligue com `REPORT_AGENDAMENTO_HABILITADO=false`.

## Testes

```bash
mvn verify
```

Precisa de Docker: os testes de integração sobem PostgreSQL, RabbitMQ e MinIO com Testcontainers. O mesmo comando roda no CI (`.github/workflows/ci.yml`) em push e PR para `develop` e `main`.

A imagem oficial `minio/minio` deixou de ser publicada; os testes usam o build comunitário `pgsty/minio`, compatível com a API S3.

## Docker

```bash
docker build -t si-report-service .
docker run --rm -p 8080:8080 --env-file .env si-report-service
```

Build multi-stage (Maven → JRE 21 Alpine), roda com usuário não root e tem `HEALTHCHECK` em `/actuator/health`. Opções extras da JVM vão em `JAVA_TOOL_OPTIONS`. Com `--env-file`, lembre que `localhost` dentro do container é o próprio container.

### Entrada para o Docker Compose do si-infra

O compose ainda não está na `develop` do si-infra (PR #12) e o MinIO entra pela si-infra#11. Trecho pronto para colar em `services:` do `compose.yaml`, no formato dos outros serviços; ajuste o nome `minio` e as variáveis `MINIO_*` para os que forem definidos lá:

```yaml
  report-service:
    build:
      context: ../si-report-service
      dockerfile: Dockerfile
    restart: unless-stopped
    environment:
      SERVER_PORT: 8080
      DB_HOST: postgres
      DB_PORT: 5432
      DB_NAME: ${DB_NAME:-seniorinsight}
      DB_USERNAME: ${DB_USERNAME:-seniorinsight}
      DB_PASSWORD: ${DB_PASSWORD:-seniorinsight}
      RABBITMQ_HOST: rabbitmq
      RABBITMQ_PORT: 5672
      RABBITMQ_VHOST: /
      RABBITMQ_USERNAME: ${RABBITMQ_USERNAME:-seniorinsight}
      RABBITMQ_PASSWORD: ${RABBITMQ_PASSWORD:-seniorinsight}
      MINIO_ENDPOINT: http://minio:9000
      MINIO_PUBLIC_ENDPOINT: http://localhost:${MINIO_PORT:-9000}
      MINIO_ACCESS_KEY: ${MINIO_ROOT_USER:-seniorinsight}
      MINIO_SECRET_KEY: ${MINIO_ROOT_PASSWORD:-seniorinsight}
      ARMAZENAMENTO_BUCKET: ${REPORT_BUCKET:-si-reports}
      REPORT_AGENDAMENTO_HABILITADO: "true"
      KEYCLOAK_ISSUER_URI: http://localhost:${KEYCLOAK_PORT:-8180}/realms/seniorinsight
      SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWKSETURI: http://keycloak:8080/realms/seniorinsight/protocol/openid-connect/certs
    expose:
      - "8080"
    healthcheck:
      test: ["CMD-SHELL", "wget -q -O /dev/null http://127.0.0.1:8080/actuator/health || exit 1"]
      interval: 15s
      timeout: 3s
      retries: 5
      start_period: 60s
    depends_on:
      postgres:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy
      minio:
        condition: service_started
      keycloak:
        condition: service_started
```

O serviço sobe mesmo com o MinIO fora do ar (o bucket é criado no primeiro upload), então `minio` pode ficar como `service_started`.
