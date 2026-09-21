/**
 * Controllers e DTOs de importação (TDS 20.2). Upload exige {@code RECONCILIATION_ANALYST};
 * leitura é aberta a todos os papéis autenticados.
 *
 * <p>Único lugar de {@code ingestion} que pode importar {@code identity}
 * ({@code GetCurrentUserUseCase}, para resolver o autor) — mesmo padrão de
 * {@code configuration.api}/{@code evidence.api} desde o M3/M4. Também é o único lugar que
 * chama {@code configuration.application.GetSourceUseCase} para exibir {@code sourceCode}
 * nas respostas — {@code ingestion.application} não precisa disso (guarda só
 * {@code sourceId}).
 */
package dev.fincore.ingestion.api;
