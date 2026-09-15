/**
 * Casos de uso de configuração (Implementation Plan M3). {@code @PreAuthorize} vive aqui,
 * não no controller (TDS 21.2).
 *
 * <p>Nenhuma classe aqui importa {@code dev.fincore.identity} — o grafo de dependências
 * (TDS 4.2) não lista essa aresta. O ator para auditoria chega como {@code UUID}/{@code String}
 * simples; é {@code configuration.api} (parte da camada "api", que TDS 4.2 permite
 * depender de {@code identity}) quem resolve esses valores a partir do
 * {@code CurrentUser} autenticado antes de chamar o caso de uso.
 */
package dev.fincore.configuration.application;
