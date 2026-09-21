/**
 * Datas de negócio e fuso (TDS 8.4).
 *
 * <p>{@link dev.fincore.shared.time.BusinessDateResolver} deriva a data de negócio a partir
 * do instante e do fuso da fonte — a única base de comparação temporal do motor (Domain
 * §5.5). O {@code Clock} injetável mora em {@code dev.fincore.platform.ClockConfig} desde
 * o M2 (sessão precisou dele antes de haver regra de domínio sensível a data).
 */
package dev.fincore.shared.time;
