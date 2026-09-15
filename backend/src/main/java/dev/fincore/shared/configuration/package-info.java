/**
 * O contrato de configuração congelada que atravessa a fronteira entre
 * {@code configuration} (quem produz) e {@code matching} (quem consome) — TDS 4.3
 * proíbe {@code matching → configuration} explicitamente ("o motor recebe o snapshot
 * como parâmetro", P4), e o grafo de dependências (TDS 4.2) não lista aresta nenhuma
 * entre os dois módulos, em nenhum sentido.
 *
 * <p>Por isso {@link dev.fincore.shared.configuration.RunConfigSnapshot} não mora em
 * {@code configuration.domain}: {@code shared} é o único pacote que os dois lados podem
 * referenciar sem violar a proibição — o mesmo raciocínio que já pôs {@code Uuid7} em
 * {@code shared.identifier} no M1. {@code configuration.infrastructure.ConfigSnapshotFactory}
 * constrói o snapshot a partir das entidades mutáveis do módulo; o tipo em si é neutro.
 */
package dev.fincore.shared.configuration;
