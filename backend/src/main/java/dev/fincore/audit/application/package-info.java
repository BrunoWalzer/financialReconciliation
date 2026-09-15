/**
 * {@link dev.fincore.audit.application.AuditService} — o ponto único, chamado
 * explicitamente pelos casos de uso dos milestones seguintes (TDS 22.1). Sem AOP, sem
 * anotação mágica: auditoria precisa de ator, justificativa e correlação que um
 * interceptador não tem, e a chamada explícita fica visível em revisão de código.
 */
package dev.fincore.audit.application;
