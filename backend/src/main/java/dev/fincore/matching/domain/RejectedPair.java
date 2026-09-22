package dev.fincore.matching.domain;

import java.util.UUID;

/**
 * Um par humano recusado como correspondência ({@code match_rejection}, TDS 7.6, I-10).
 * Canônico por construção — a ordem dos dois ids não importa para igualdade/hash, mesmo
 * raciocínio do {@code CHECK (record_a_id < record_b_id)} da tabela.
 *
 * <p>A ordenação usa {@link Long#compareUnsigned} sobre os bits do UUID, <b>não</b>
 * {@code UUID.compareTo}: este último compara {@code mostSigBits}/{@code leastSigBits}
 * como {@code long} com sinal, enquanto o tipo {@code uuid} do PostgreSQL compara os 16
 * bytes sem sinal. As duas ordens discordam sempre que o byte mais significativo do UUID
 * tem o bit alto ligado (ex.: {@code 80000000-...} vs {@code 00000001-...}) — descoberto
 * por um teste de integração real contra Postgres que falhava de forma intermitente
 * (ver relatório do M9, seção Desvios). Sem esta correção, um par que o Java considera
 * canônico podia violar {@code ck_match_rejection_canonical_order} no banco.
 */
public record RejectedPair(UUID a, UUID b) {

    public RejectedPair {
        if (comparePostgresUuidOrder(a, b) > 0) {
            UUID swap = a;
            a = b;
            b = swap;
        }
    }

    public static RejectedPair of(UUID first, UUID second) {
        return new RejectedPair(first, second);
    }

    private static int comparePostgresUuidOrder(UUID a, UUID b) {
        int mostSignificant = Long.compareUnsigned(a.getMostSignificantBits(), b.getMostSignificantBits());
        if (mostSignificant != 0) {
            return mostSignificant;
        }
        return Long.compareUnsigned(a.getLeastSignificantBits(), b.getLeastSignificantBits());
    }
}
