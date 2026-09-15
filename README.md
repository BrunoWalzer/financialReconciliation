# FINCORE

Plataforma de conciliação financeira. O produto não diz apenas que duas fontes divergem —
ele explica **por quê**, com evidência rastreável até o registro que a originou.

Monólito modular em Java 21 / Spring Boot 3.5 / PostgreSQL 16. A correção financeira é
garantida por constraints de banco, não por código de aplicação.

**Estado atual: M2 — Identity, Authentication & Authorization.** Login, refresh rotativo
com detecção de reuso, logout, papéis (`RECONCILIATION_ANALYST`, `AUDITOR`,
`ADMINISTRATOR`) e o primeiro endpoint de consulta (`GET /audit-events`) existem. Ainda não
há endpoint de negócio nenhum — o esqueleto de autorização está pronto para eles, mas
`configuration`, `evidence`, `ingestion`, `matching`, `reconciliation`, `divergence` e
`analytics` seguem vazios, um módulo por milestone.

## Documentos

Estes três são a fonte de verdade, nesta ordem de precedência em caso de conflito:

| Documento | Decide |
|---|---|
| [Product & Domain Specification v1.1](docs/FINCORE-PRODUCT-DOMAIN-SPEC-v1.1.md) | regra de negócio |
| [Technical Design Specification v1.1](docs/FINCORE-TECHNICAL-DESIGN-SPEC-v1.1.md) | como construir |
| [Implementation Plan](docs/FINCORE-IMPLEMENTATION-PLAN.md) | ordem dos milestones |

## Pré-requisitos

- JDK 21 — se a máquina não tiver, o Gradle baixa o toolchain sozinho.
- Docker, para o PostgreSQL de desenvolvimento e para os testes com Testcontainers.

## Como subir

```bash
cp .env.example .env          # ajuste se quiser; os padrões servem para desenvolvimento
docker compose up -d          # PostgreSQL 16, com healthcheck e volume nomeado
./gradlew :backend:bootRun    # perfil local por padrão
```

Verificação:

```bash
curl -s localhost:8080/actuator/health              # {"status":"UP"}
curl -s localhost:8080/actuator/health/readiness
curl -s localhost:8080/actuator/info                # versão e ruleSetVersion
curl -s localhost:8080/actuator/prometheus | head
```

Toda resposta traz `X-Correlation-Id` — o enviado pelo cliente, quando válido, ou um gerado:

```bash
curl -si localhost:8080/rota-que-nao-existe -H 'X-Correlation-Id: minha-correlacao'
```

A resposta é RFC 9457 (`application/problem+json`) com `code` e `correlationId`, e nunca
carrega stack trace, SQL, nome de tabela ou de constraint.

Login com o administrador semeado (senha vem de `FINCORE_BOOTSTRAP_ADMIN_PASSWORD`):

```bash
curl -si localhost:8080/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"admin@fincore.dev","password":"ChangeMe123!"}'
# {"accessToken":"eyJ...","tokenType":"Bearer","expiresInSeconds":900}
# Set-Cookie: refreshToken=...; Path=/api/v1/auth; HttpOnly; Secure; SameSite=Strict

curl -s localhost:8080/auth/me -H 'Authorization: Bearer <accessToken>'
curl -s localhost:8080/audit-events -H 'Authorization: Bearer <accessToken>'
```

## Como testar

```bash
./gradlew :backend:architectureTest   # ArchUnit: as fronteiras da TDS 4.4
./gradlew :backend:test               # unitários, sem Docker
./gradlew :backend:integrationTest    # Testcontainers PostgreSQL
./gradlew :backend:check              # os três, nesta ordem
```

A ordem é deliberada: com a fronteira arquitetural quebrada, o resto é ruído. O CI segue a
mesma ordem.

## Estrutura

```text
backend/src/main/java/dev/fincore/
  shared/       money · time · error · correlation · identifier   base comum, folha do grafo
  platform/     configuração Spring, web, security                infraestrutura transversal
  audit/        domain · application · infrastructure · api       AuditEvent, ActorRef, AuditService (M1); GET /audit-events (M2)
  identity/     domain · application · infrastructure · api       AppUser, RefreshToken, LoginThrottle; /auth/* (M2)
  configuration/ evidence/ ingestion/
  matching/ reconciliation/ divergence/ analytics/                um módulo por milestone
backend/src/main/resources/db/migration/                          V0.. forward-only, SQL explícito
backend/src/test/java/dev/fincore/
  architecture/   ArchUnit, mais as classes que violam de propósito
  infrastructure/ migrations e superfície do Actuator
  audit/          testes de domínio, serviço, constraint e API de audit_event
  identity/       testes de domínio, casos de uso, API e concorrência de identidade
  platform/security/ CORS
```

Os módulos ainda vazios existem com um `package-info.java` que diz qual milestone os
preenche. As regras arquiteturais já cobrem todos eles: passam vacuamente hoje e ficam
armadas.

## Migrations

SQL explícito, forward-only, nunca editadas depois de aplicadas. Toda constraint com nome
explícito — a tradução de violação para erro de API depende do nome. Seed de
desenvolvimento nunca entra em migration versionada.

## Auditoria

`audit_event` é imutável: um trigger (`fincore_reject_mutation()`) rejeita `UPDATE` e
`DELETE` no banco — a barreira definitiva, provada por teste que tenta violar. Nenhum
módulo grava nele diretamente; todos chamam `AuditService.record(...)` explicitamente, sem
AOP e sem anotação mágica (TDS 22.1), na mesma transação da mudança que estão registrando.

Desde o M2, `LoginUseCase`, `RefreshSessionUseCase` e `ChangeUserRoleUseCase` chamam
`AuditService` explicitamente — login, falha de login, reuso de refresh detectado e troca
de papel. Renovação de token rotineira e logout **não** são auditados (TDS 22.3 lista
"renovação de token" entre o que não entra).

## Autenticação

JWT HS256 (`FINCORE_JWT_SECRET`, 15 min) para o access token; refresh opaco de 256 bits,
hash SHA-256 no banco, 7 dias, transportado em cookie `HttpOnly; Secure; SameSite=Strict;
Path=/api/v1/auth`. Cada uso do refresh o rotaciona — o anterior nunca mais é aceito. Um
refresh já rotacionado (ou revogado por qualquer motivo) apresentado de novo revoga **toda
a família** de sessões daquele token e audita — o sinal clássico de token roubado, e o
sistema não distingue isso de uma corrida benigna: a política é queimar tudo e forçar novo
login (`RefreshTokenConcurrencyTest` prova isso com threads reais).

Bloqueio de login é persistido em `login_throttle`, não em memória: sobrevive a reinício.
5 falhas bloqueiam; cada 5 falhas adicionais dobram a duração do bloqueio (números não
especificados nos documentos-fonte — ver relatório do M2). Senha errada e e-mail
inexistente produzem a mesma resposta, no mesmo tempo.

Três papéis (`RECONCILIATION_ANALYST`, `AUDITOR`, `ADMINISTRATOR`); `@PreAuthorize` vive
nos casos de uso, não nos controllers.

## Notas de ambiente

**Caminho com acento.** A JVM que o Gradle forka lê o classpath de um argfile usando a
codificação nativa do sistema. Em um caminho como `C:\Users\Usuário\...`, isso falha se
`file.encoding` não coincidir. O `gradle.properties` resolve com `-Dfile.encoding=COMPAT`.

**Pasta sincronizada pelo OneDrive.** Dois sintomas distintos, mesma causa.

1. *Diretório somente-leitura.* O OneDrive marca diretórios como somente-leitura enquanto
   sincroniza, e o Windows recusa remover diretório com esse atributo — o Gradle falha ao
   limpar `backend/build` com `Unable to delete directory`. Contorno:
   ```bash
   cmd //c "attrib -r /s /d backend\build\*"
   ```
2. *Arquivo marcado como reparse point.* O OneDrive marca arquivo já sincronizado como
   "placeholder" (mecanismo de Files On-Demand) mesmo já hidratado localmente — o
   snapshotter do Gradle recusa com `Cannot snapshot ...: not a regular file`, tipicamente
   em `processResources`/`processTestResources` logo depois de editar `.yml`/`.sql`.
   Reescrever o arquivo no lugar não remove a marca; é preciso apagar e recriar:
   ```powershell
   $p = "caminho\do\arquivo"
   $bytes = [IO.File]::ReadAllBytes($p); Remove-Item -Force $p; [IO.File]::WriteAllBytes($p, $bytes)
   ```

Mantenha o repositório fora de uma pasta sincronizada, ou exclua-a da sincronização — é a
correção real; os contornos acima são só para esta sessão de trabalho.
