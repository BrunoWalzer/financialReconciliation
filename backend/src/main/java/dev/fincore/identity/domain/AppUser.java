package dev.fincore.identity.domain;

import dev.fincore.shared.identifier.Uuid7;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Usuário autenticado do FINCORE (Domain §4; TDS 7.1).
 *
 * <p>Aggregate root do módulo {@code identity}. Invariantes: e-mail único (constraint de
 * banco {@code uq_app_user_email} — a linha de defesa definitiva) e conjunto de papéis
 * restrito a {@link UserRole}.
 *
 * <p>{@code user_role} é uma tabela de atributo puro (PK {@code (user_id, role)}, nenhuma
 * outra coluna) — mapeada como {@code @ElementCollection}, não como entidade própria: não
 * há identidade nem ciclo de vida independente do papel em relação ao usuário.
 */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @ElementCollection(targetClass = UserRole.class, fetch = FetchType.EAGER)
    @CollectionTable(name = "user_role", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Set<UserRole> roles = EnumSet.noneOf(UserRole.class);

    /** Exigido pelo JPA. Nunca chamado pela aplicação. */
    protected AppUser() {
    }

    public AppUser(String email, String passwordHash, String displayName, Set<UserRole> roles, Instant now) {
        this.id = Uuid7.generate();
        this.email = requireNonBlank(email, "email");
        this.passwordHash = requireNonBlank(passwordHash, "passwordHash");
        this.displayName = requireNonBlank(displayName, "displayName");
        this.active = true;
        this.createdAt = Objects.requireNonNull(now, "now é obrigatório");
        this.updatedAt = now;
        this.roles = EnumSet.copyOf(nonEmpty(roles));
    }

    public UUID id() {
        return id;
    }

    public String email() {
        return email;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public String displayName() {
        return displayName;
    }

    public boolean active() {
        return active;
    }

    public Set<UserRole> roles() {
        return Set.copyOf(roles);
    }

    /**
     * Concede um papel. Alteração de papel é uma das operações mais sensíveis do sistema
     * (Domain §27.2) — quem chama isto é responsabilidade do caso de uso, que audita
     * antes/depois (TDS 16.1).
     */
    public void grantRole(UserRole role, Instant now) {
        roles.add(Objects.requireNonNull(role, "role é obrigatório"));
        this.updatedAt = Objects.requireNonNull(now, "now é obrigatório");
    }

    /** @throws IllegalStateException se {@code role} for o último papel do usuário */
    public void revokeRole(UserRole role, Instant now) {
        Objects.requireNonNull(role, "role é obrigatório");
        if (roles.contains(role) && roles.size() == 1) {
            throw new IllegalStateException("usuário precisa manter ao menos um papel");
        }
        roles.remove(role);
        this.updatedAt = Objects.requireNonNull(now, "now é obrigatório");
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " é obrigatório");
        }
        return value;
    }

    private static Set<UserRole> nonEmpty(Set<UserRole> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("roles não pode ser vazio");
        }
        return roles;
    }
}
