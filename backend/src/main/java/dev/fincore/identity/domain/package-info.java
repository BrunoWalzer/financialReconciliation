/**
 * O agregado de usuário e os valores em torno de autenticação (Implementation Plan M2).
 *
 * <p>Sem dependência de Spring além das anotações de mapeamento do Hibernate (TDS 4.4) —
 * os VOs ({@link dev.fincore.identity.domain.AccessToken},
 * {@link dev.fincore.identity.domain.CurrentUser},
 * {@link dev.fincore.identity.domain.TokenFamily},
 * {@link dev.fincore.identity.domain.RefreshTokenSecret}) nem isso têm.
 */
package dev.fincore.identity.domain;
