/**
 * Casos de uso de evidência (Implementation Plan M4). {@code @PreAuthorize} vive aqui, não
 * no controller (TDS 21.2).
 *
 * <p>Nenhuma classe aqui importa {@code dev.fincore.identity} nem
 * {@code dev.fincore.configuration} — o grafo de dependências (TDS 4.2) não lista essas
 * arestas para {@code evidence}. O ator para auditoria chega como {@code UUID}/{@code String}
 * simples, e o {@code sourceId} de um filtro por {@code sourceCode} chega já resolvido; é
 * {@code evidence.api} (parte da camada "api") quem faz as duas resoluções antes de chamar
 * o caso de uso — mesmo padrão de {@code configuration.api} no M3.
 *
 * <p>Depende de {@code audit} ({@code AuditService}) porque a criação de uma anotação é
 * evento auditado (TDS 22.2: "anotação criada") — extensão explícita do grafo original, que
 * só listava {@code evidence --> shared}; ver o relatório do M4, seção Decisões.
 */
package dev.fincore.evidence.application;
