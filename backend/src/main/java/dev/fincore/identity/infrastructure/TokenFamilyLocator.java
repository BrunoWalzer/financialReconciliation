package dev.fincore.identity.infrastructure;

import dev.fincore.identity.domain.RefreshToken;
import dev.fincore.identity.domain.TokenFamily;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Monta um {@link TokenFamily} caminhando {@code replaced_by_id} nos dois sentidos a
 * partir de um token qualquer da linhagem — não há coluna de família no banco (TDS 7.1
 * não lista uma), então a família é um conceito derivado, não armazenado.
 *
 * <p>Cada token tem no máximo um sucessor e no máximo um predecessor (garantido por
 * {@code uq_refresh_token_replaced_by_id} e pela PK), então a cadeia nunca se ramifica —
 * a busca é O(tamanho da cadeia), aceitável no volume deste milestone.
 */
@Component
public class TokenFamilyLocator {

    private final RefreshTokenRepository repository;

    public TokenFamilyLocator(RefreshTokenRepository repository) {
        this.repository = repository;
    }

    public TokenFamily locate(RefreshToken anyMember) {
        RefreshToken root = walkToRoot(anyMember);
        List<RefreshToken> members = new ArrayList<>();
        RefreshToken current = root;
        while (current != null) {
            members.add(current);
            current = current.replacedById() != null
                    ? repository.findById(current.replacedById()).orElse(null)
                    : null;
        }
        return new TokenFamily(members);
    }

    private RefreshToken walkToRoot(RefreshToken start) {
        RefreshToken current = start;
        Optional<RefreshToken> predecessor = repository.findByReplacedById(current.id());
        while (predecessor.isPresent()) {
            current = predecessor.get();
            predecessor = repository.findByReplacedById(current.id());
        }
        return current;
    }
}
