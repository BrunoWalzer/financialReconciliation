/**
 * Classes que violam de proposito as regras de {@link dev.fincore.architecture.ArchitectureRules}.
 *
 * <p>Existem para provar que as regras detectam violacao. Uma regra que nunca reprovou
 * nada e indistinguivel de uma regra quebrada — e o M0 arma quinze delas sobre pacotes
 * ainda vazios, onde todas passam vacuamente.
 *
 * <p>Sao codigo de teste: o {@code ImportOption.DoNotIncludeTests} usado pela analise do
 * codigo de producao as exclui, e nenhuma delas chega ao artefato da aplicacao.
 */
package dev.fincore.architecture.violation;
