package dev.fincore.configuration.domain;

import dev.fincore.shared.identifier.Uuid7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A origem lógica de registros financeiros que uma importação (M5) e uma evidência (M4)
 * referenciarão — mas nenhum valor financeiro mora aqui (Domain §5; TDS 7.3).
 *
 * <p><b>Não é {@code FinancialRecord}.</b> Guarda como interpretar um arquivo dessa fonte
 * (fuso, separadores, formatos de data aceitos, arredondamento), nunca um valor bruto,
 * taxa, líquido, moeda de uma transação, data de negócio, NSU, documento da contraparte
 * ou descrição — esses conceitos pertencem à evidência (M4) e nascem por importação.
 *
 * <p>Só nasce por seed de referência (V3, migration versionada) neste milestone — não há
 * endpoint de mutação para {@code Source} no M3 (Implementation Plan: só
 * {@code GET /config/sources}).
 */
@Entity
@Table(name = "source")
public class Source {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "code", nullable = false, updatable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "timezone", nullable = false)
    private String timezone;

    @Column(name = "decimal_separator", nullable = false)
    private String decimalSeparator;

    @Column(name = "thousands_separator", nullable = false)
    private String thousandsSeparator;

    // Mapeado direto para a coluna nativa text[] (TDS 7.3) — sem tabela filha: Hibernate
    // 6 sabe converter List<String> para ARRAY do Postgres com este código de tipo JDBC.
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "date_formats", nullable = false)
    private List<String> dateFormats;

    @Enumerated(EnumType.STRING)
    @Column(name = "rounding_mode", nullable = false)
    private RoundingMode roundingMode;

    @Column(name = "active", nullable = false)
    private boolean active;

    /** Exigido pelo JPA. Nunca chamado pela aplicação. */
    protected Source() {
    }

    public Source(
            String code,
            String name,
            String timezone,
            String decimalSeparator,
            String thousandsSeparator,
            List<String> dateFormats,
            RoundingMode roundingMode) {
        this.id = Uuid7.generate();
        this.code = requireNonBlank(code, "code");
        this.name = requireNonBlank(name, "name");
        this.timezone = requireNonBlank(timezone, "timezone");
        this.decimalSeparator = requireNonBlank(decimalSeparator, "decimalSeparator");
        this.thousandsSeparator = requireNonBlank(thousandsSeparator, "thousandsSeparator");
        this.dateFormats = List.copyOf(nonEmpty(dateFormats, "dateFormats"));
        this.roundingMode = Objects.requireNonNull(roundingMode, "roundingMode é obrigatório");
        this.active = true;
    }

    public UUID id() {
        return id;
    }

    public String code() {
        return code;
    }

    public String name() {
        return name;
    }

    public String timezone() {
        return timezone;
    }

    public String decimalSeparator() {
        return decimalSeparator;
    }

    public String thousandsSeparator() {
        return thousandsSeparator;
    }

    public List<String> dateFormats() {
        return List.copyOf(dateFormats);
    }

    public RoundingMode roundingMode() {
        return roundingMode;
    }

    public boolean active() {
        return active;
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " é obrigatório");
        }
        return value;
    }

    private static List<String> nonEmpty(List<String> values, String field) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException(field + " não pode ser vazio");
        }
        return values;
    }
}
