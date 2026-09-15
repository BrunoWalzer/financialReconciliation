# FINCORE

Plataforma de conciliação financeira. O produto não diz apenas que duas fontes divergem —
ele explica **por quê**, com evidência rastreável até o registro que a originou.

Monólito modular em Java 21 / Spring Boot 3.5 / PostgreSQL 16. A correção financeira é
garantida por constraints de banco, não por código de aplicação.

**Estado atual: M1 — Audit Foundation.** A aplicação sobe, falha no formato certo, o CI
recusa violação de fronteira arquitetural, e o primeiro registro imutável (`audit_event`)
existe e é gravado por `AuditService`. Não há identidade, autenticação nem API de negócio
ainda — nenhum endpoint chama `AuditService` de fora, porque não há caso de uso que precise
auditar algo.

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
  platform/     configuração Spring, web                          infraestrutura transversal
  audit/        domain · application · infrastructure             AuditEvent, ActorRef, AuditService (M1)
  identity/ configuration/ evidence/ ingestion/
  matching/ reconciliation/ divergence/ analytics/                um módulo por milestone
backend/src/main/resources/db/migration/                          V0.. forward-only, SQL explícito
backend/src/test/java/dev/fincore/
  architecture/   ArchUnit, mais as classes que violam de propósito
  infrastructure/ migrations e superfície do Actuator
  audit/          testes de domínio, serviço e constraint de audit_event
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

Não há ainda nenhum caso de uso chamando `AuditService`: não existe caso de uso. O
serviço existe primeiro, sozinho, para que o milestone que criar o primeiro (M2, com
login) já nasça com rastro.

## Notas de ambiente

**Caminho com acento.** A JVM que o Gradle forka lê o classpath de um argfile usando a
codificação nativa do sistema. Em um caminho como `C:\Users\Usuário\...`, isso falha se
`file.encoding` não coincidir. O `gradle.properties` resolve com `-Dfile.encoding=COMPAT`.

**Pasta sincronizada pelo OneDrive.** O OneDrive marca diretórios como somente-leitura
enquanto sincroniza, e o Windows recusa remover diretório com esse atributo — o Gradle
falha ao limpar `backend/build` com `Unable to delete directory`. Mantenha o repositório
fora de uma pasta sincronizada, ou exclua-a da sincronização. Como contorno pontual:

```bash
cmd //c "attrib -r /s /d backend\build\*"
```
