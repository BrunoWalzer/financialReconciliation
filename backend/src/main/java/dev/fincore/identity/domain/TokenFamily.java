package dev.fincore.identity.domain;

import java.time.Instant;
import java.util.List;

/**
 * A linhagem completa de um {@link RefreshToken} através de rotações sucessivas
 * (TDS 21.1). Não existe coluna de "família" no banco — este VO é montado pela
 * infraestrutura caminhando {@code replacedById} nos dois sentidos a partir de um membro
 * qualquer, e representa apenas o resultado: revogar a família inteira de uma vez, a
 * reação correta quando um token já rotacionado é apresentado de novo.
 *
 * <p>Não sabe persistir nada — quem chama {@link #revokeAll} ainda precisa salvar cada
 * membro através do repositório.
 */
public record TokenFamily(List<RefreshToken> members) {

    public TokenFamily {
        members = List.copyOf(members);
    }

    /** Revoga todo membro ainda ativo. Idempotente: já-revogado não é tocado de novo. */
    public void revokeAll(Instant now) {
        for (RefreshToken member : members) {
            if (!member.isRevoked()) {
                member.revoke(now);
            }
        }
    }
}
