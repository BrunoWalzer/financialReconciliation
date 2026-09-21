/**
 * A evidência financeira imutável e o que se aprendeu sobre ela (Implementation Plan M4;
 * TDS 4.1): {@link dev.fincore.evidence.domain.FinancialRecord} e
 * {@link dev.fincore.evidence.domain.RecordAnnotation}.
 *
 * <p>{@code RecordIntegrityFlag} não tem classe de domínio ainda — a tabela nasce na
 * migration V4 (TDS 7.5), mas nada neste milestone escreve ou lê nela: a varredura que a
 * popula é do M7 (Implementation Plan). Mapear a entidade agora seria código sem nenhum
 * caso de uso, o oposto do que a seção 10 do prompt do M4 pede.
 *
 * <p>{@code FinancialRecord} não tem status de conciliação (Domain §5.2, §5.6) — esse
 * conceito é de {@code Match} e {@code Divergence}, que este módulo não conhece.
 */
package dev.fincore.evidence.domain;
