/**
 * Persistência de evidência (Implementation Plan M4). {@link dev.fincore.evidence.infrastructure.FinancialRecordRepository}
 * e {@link dev.fincore.evidence.infrastructure.RecordAnnotationRepository} expõem só
 * {@code save} e leitura — nenhum {@code update}/{@code delete}, reforçando em código a
 * imutabilidade que a trigger de banco garante (V4).
 */
package dev.fincore.evidence.infrastructure;
