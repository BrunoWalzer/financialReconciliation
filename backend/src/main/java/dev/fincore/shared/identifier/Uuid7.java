package dev.fincore.shared.identifier;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

/**
 * Gera identificadores UUID versão 7 (RFC 9562): 48 bits de timestamp em milissegundos,
 * seguidos de bits aleatórios. Diferente do UUID v4, cresce ordenado no tempo — o que
 * mantém a localidade do índice primário em tabelas de alto volume (TDS 7).
 *
 * <p>Usa o "Método 1" da RFC (rand_a e rand_b totalmente aleatórios, sem contador
 * monotônico): mais simples, e suficiente para o volume do FINCORE. Dois identificadores
 * gerados no mesmo milissegundo não têm ordem relativa garantida entre si — a RFC permite
 * isso explicitamente.
 *
 * <p>Único ponto de geração de identificador do FINCORE.
 */
public final class Uuid7 {

    private static final SecureRandom RANDOM = new SecureRandom();

    /** Nibble de versão (0111) na posição dos bits 12-15 dos 64 bits mais significativos. */
    private static final long VERSION_NIBBLE = 0x7L << 12;

    /** Bit 63 ligado, bit 62 desligado: variante RFC 4122 nos 64 bits menos significativos. */
    private static final long VARIANT_BITS = 0x8000000000000000L;

    /** Zera os dois bits mais significativos antes de aplicar {@link #VARIANT_BITS}. */
    private static final long CLEAR_VARIANT_MASK = 0x3FFFFFFFFFFFFFFFL;

    /** O timestamp ocupa 48 bits; qualquer bit acima é descartado. */
    private static final long TIMESTAMP_MASK_48_BITS = 0xFFFFFFFFFFFFL;

    /** `rand_a` tem 12 bits: valores de 0 a 4095. */
    private static final int RAND_A_BOUND = 1 << 12;

    private Uuid7() {
    }

    public static UUID generate() {
        long timestamp = Instant.now().toEpochMilli() & TIMESTAMP_MASK_48_BITS;
        long randA = RANDOM.nextInt(RAND_A_BOUND);
        long randB = RANDOM.nextLong();

        long mostSignificantBits = (timestamp << 16) | VERSION_NIBBLE | randA;
        long leastSignificantBits = (randB & CLEAR_VARIANT_MASK) | VARIANT_BITS;

        return new UUID(mostSignificantBits, leastSignificantBits);
    }
}
