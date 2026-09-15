/**
 * Geração de identificadores (TDS 7: "identificadores UUID v7").
 *
 * <p>Nasce no M1, quando a primeira entidade ({@code AuditEvent}) precisa de uma chave
 * primária. Nem o PostgreSQL 16 nem o Hibernate 6.6 — a versão empacotada pelo Spring Boot
 * 3.5 — geram UUID v7 nativamente (verificado por inspeção do jar de
 * {@code hibernate-core:6.6.53.Final}: {@code org.hibernate.annotations.UuidGenerator.Style}
 * só tem {@code AUTO}, {@code RANDOM} e {@code TIME}). Por isso o identificador nasce na
 * aplicação, num único ponto reutilizável por qualquer módulo.
 */
package dev.fincore.shared.identifier;
