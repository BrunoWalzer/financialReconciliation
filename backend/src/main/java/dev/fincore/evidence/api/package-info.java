/**
 * Controllers e DTOs de evidência (TDS 20.2; Implementation Plan M4). Leitura para todos os
 * papéis autenticados; criar anotação exige {@code RECONCILIATION_ANALYST}.
 *
 * <p>Único lugar de {@code evidence} que pode importar {@code identity}
 * ({@code GetCurrentUserUseCase}, {@code CurrentUser}, para resolver o autor de uma
 * anotação) e {@code configuration} ({@code GetSourceByCodeUseCase}, para resolver o filtro
 * {@code sourceCode} de {@code GET /records}) — a camada api tem essas arestas permitidas,
 * mesmo padrão de {@code configuration.api} no M3.
 */
package dev.fincore.evidence.api;
