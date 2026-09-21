/**
 * {@link dev.fincore.ingestion.domain.ImportBatch} e
 * {@link dev.fincore.ingestion.domain.RejectedRecord} (Domain §9; TDS 7.4).
 *
 * <p>{@code ImportBatch} é mutável até o estado terminal (Domain §6.3) — diferente de
 * {@code FinancialRecord}, não tem trigger de imutabilidade; a máquina de estados é a
 * própria proteção. {@code RejectedRecord} é append-only.
 */
package dev.fincore.ingestion.domain;
