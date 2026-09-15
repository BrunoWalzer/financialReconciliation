package dev.fincore.configuration.domain;

import dev.fincore.shared.identifier.Uuid7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.Objects;
import java.util.UUID;

/** Periodicidade esperada de chegada de dados de uma fonte, e a folga antes de virar ausência (TDS 7.3). */
@Entity
@Table(name = "coverage_expectation")
public class CoverageExpectation {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "source_id", nullable = false, updatable = false)
    private UUID sourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "schedule", nullable = false)
    private CoverageSchedule schedule;

    @Column(name = "grace_days", nullable = false)
    private int graceDays;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /** Exigido pelo JPA. Nunca chamado pela aplicação. */
    protected CoverageExpectation() {
    }

    public CoverageExpectation(UUID sourceId, CoverageSchedule schedule, int graceDays) {
        this.id = Uuid7.generate();
        this.sourceId = Objects.requireNonNull(sourceId, "sourceId é obrigatório");
        this.schedule = Objects.requireNonNull(schedule, "schedule é obrigatório");
        this.graceDays = graceDays;
        this.active = true;
    }

    public void update(CoverageSchedule schedule, int graceDays) {
        this.schedule = Objects.requireNonNull(schedule, "schedule é obrigatório");
        this.graceDays = graceDays;
    }

    public UUID id() {
        return id;
    }

    public UUID sourceId() {
        return sourceId;
    }

    public CoverageSchedule schedule() {
        return schedule;
    }

    public int graceDays() {
        return graceDays;
    }

    public boolean active() {
        return active;
    }

    public long version() {
        return version;
    }
}
