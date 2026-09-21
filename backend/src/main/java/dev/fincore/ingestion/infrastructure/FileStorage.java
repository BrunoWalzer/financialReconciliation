package dev.fincore.ingestion.infrastructure;

/**
 * Porta de armazenamento do arquivo original (Domain §9.1: "preservado integralmente").
 * Uma implementação só — filesystem (OD-4, Implementation Plan FD-10) — nunca S3/MinIO em
 * milestone nenhum deste projeto.
 */
public interface FileStorage {

    /** Grava o conteúdo e devolve a chave para reler depois. */
    String store(String storageKeyHint, byte[] content);

    byte[] read(String storageKey);
}
