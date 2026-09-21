/**
 * O caso de uso de importação síncrona (Implementation Plan M5/M6; TDS 9.2). O ator chega
 * como {@code UUID}/{@code String} simples — {@code ingestion.api} resolve
 * {@code CurrentUser} antes de chamar, mesmo padrão de {@code configuration.api} desde o
 * M3.
 *
 * <p>{@link dev.fincore.ingestion.application.ImportBatchTransactionalSteps} existe
 * separado de {@link dev.fincore.ingestion.application.ImportFileUseCase} por uma razão
 * técnica precisa: o orquestrador não é {@code @Transactional} (a fronteira é por lote de
 * 1000 linhas, não a operação inteira), e chamar um método {@code @Transactional} por
 * {@code this} dentro da própria classe não passa pelo proxy do Spring — a anotação seria
 * decorativa.
 */
package dev.fincore.ingestion.application;
