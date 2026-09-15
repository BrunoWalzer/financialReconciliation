/**
 * Utilitários HTTP genéricos o bastante para não pertencer a nenhum módulo, e usados por
 * mais de um — mesmo critério que já pôs {@link dev.fincore.shared.correlation.CorrelationId}
 * aqui em vez de {@code platform.web} desde o M0.
 *
 * <p>{@link dev.fincore.shared.web.ETag} nasceu no M3 justamente por esse motivo: um
 * controller de módulo (ex.: {@code configuration.api}) precisa chamá-lo, e
 * {@code platform.web.GlobalErrorHandler} precisa capturar exceções desse mesmo módulo —
 * as duas coisas ao mesmo tempo criariam um ciclo se {@code ETag} morasse em
 * {@code platform}. {@code shared} não depende de nada, então não há ciclo possível.
 */
package dev.fincore.shared.web;
