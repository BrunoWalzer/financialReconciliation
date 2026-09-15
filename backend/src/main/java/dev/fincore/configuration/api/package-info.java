/**
 * Controllers e DTOs de configuração (TDS 19.2; Implementation Plan M3). Todos os
 * endpoints são {@code ADMINISTRATOR}, todos com {@code If-Match} em mutação.
 *
 * <p>Único lugar de {@code configuration} que pode importar {@code identity}
 * ({@code GetCurrentUserUseCase}, {@code CurrentUser}) — a camada api tem essa aresta
 * permitida (TDS 4.2); {@code configuration.application} não tem.
 */
package dev.fincore.configuration.api;
