/**
 * O agregado de auditoria e o valor que identifica quem agiu (Implementation Plan M1).
 *
 * <p>Sem dependência de Spring além das anotações de mapeamento do Hibernate (TDS 4.4) —
 * {@link dev.fincore.audit.domain.ActorRef} nem isso tem: é um {@code record} puro.
 */
package dev.fincore.audit.domain;
