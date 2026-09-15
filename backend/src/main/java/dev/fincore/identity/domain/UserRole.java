package dev.fincore.identity.domain;

/**
 * Os três papéis do FINCORE, cada um justificado por necessidade real (Domain §4).
 *
 * <p>Acúmulo de papéis por um mesmo usuário é permitido e explícito (Domain §4.3) — daí
 * {@code AppUser} guardar um conjunto, não um valor único.
 */
public enum UserRole {

    /** O usuário central: importa, dispara execução, resolve divergência (Domain §4.1). */
    RECONCILIATION_ANALYST,

    /** Somente leitura — inclusive do log de auditoria. Escreve, deixa de ser auditor (Domain §4.2). */
    AUDITOR,

    /** Gerencia usuários e configuração. Não resolve divergência, por desenho (Domain §4.3). */
    ADMINISTRATOR
}
