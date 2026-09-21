package dev.fincore.ingestion.application;

/** O arquivo original de uma importação, para download (Domain §9.1: "preservado integralmente"). */
public record DownloadedFile(String filename, byte[] content) {
}
