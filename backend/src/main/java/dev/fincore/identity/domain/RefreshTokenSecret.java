package dev.fincore.identity.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * O segredo por trás de um {@link RefreshToken}: opaco, 256 bits, nunca persistido em
 * claro (TDS 21.1) — só o hash SHA-256 dele vai para {@code refresh_token.token_hash}.
 *
 * <p>Duas operações, sem estado: {@link #generate()} para emitir um token novo,
 * {@link #hash(String)} para comparar um token apresentado com o que está no banco.
 */
public final class RefreshTokenSecret {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int RAW_BYTES = 32; // 256 bits

    private RefreshTokenSecret() {
    }

    /** Um segredo novo, aleatório, codificado para uso seguro em cookie. */
    public static String generate() {
        byte[] bytes = new byte[RAW_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 do segredo, em hexadecimal — o que efetivamente é comparado no banco. */
    public static String hash(String rawSecret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawSecret.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 é obrigatório em toda implementação de JVM (java.security.MessageDigest).
            throw new IllegalStateException("SHA-256 indisponível na JVM", e);
        }
    }
}
