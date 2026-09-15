/**
 * Configuração de segurança HTTP transversal (TDS 4.4: {@code platform} hospeda
 * "config Spring, segurança"). O que é específico de como o FINCORE autentica —
 * emissão/verificação de JWT, o filtro que lê o cabeçalho, hashing de senha — mora em
 * {@link dev.fincore.identity.infrastructure} (Implementation Plan M2); aqui fica só a
 * fiação do {@code SecurityFilterChain}: quais rotas são públicas, CORS, e como uma
 * falha de autenticação/autorização vira {@code application/problem+json} (M2 §14: "não
 * crie um segundo sistema de tratamento de erros").
 */
package dev.fincore.platform.security;
