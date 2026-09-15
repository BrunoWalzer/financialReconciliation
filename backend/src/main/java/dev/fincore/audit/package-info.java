/**
 * Registro imutável de decisões (TDS 4.1) — folha do grafo, só depende de {@code shared}.
 *
 * <p>Preenchido a partir do M1: {@link dev.fincore.audit.domain} traz o agregado e o VO de
 * ator; {@link dev.fincore.audit.application} traz o serviço que os módulos seguintes
 * chamam explicitamente; {@link dev.fincore.audit.infrastructure} traz a persistência.
 * Não há {@code audit.api} ainda — a consulta HTTP entra no M2, quando já houver
 * autorização para protegê-la.
 */
package dev.fincore.audit;
