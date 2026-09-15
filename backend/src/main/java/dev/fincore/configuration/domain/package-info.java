/**
 * As seis entidades de configuração (Implementation Plan M3): {@code Source},
 * {@code SourcePair}, {@code ToleranceConfig}, {@code FeeRule}, {@code SettlementWindow},
 * {@code CoverageExpectation}. Regra de negócio parametrizada (TDS 30.1): muda se uma
 * conciliação fecha, mora em tabela, é auditada.
 *
 * <p>{@link dev.fincore.shared.configuration.RunConfigSnapshot} não mora aqui — ver o
 * Javadoc daquele pacote.
 */
package dev.fincore.configuration.domain;
