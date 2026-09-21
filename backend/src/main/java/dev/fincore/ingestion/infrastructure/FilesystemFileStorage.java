package dev.fincore.ingestion.infrastructure;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Único adaptador de {@link FileStorage}: diretório local (Implementation Plan FD-10, OD-4). */
@Component
public class FilesystemFileStorage implements FileStorage {

    private final Path rootDirectory;

    public FilesystemFileStorage(@Value("${fincore.ingestion.file-storage-path}") String rootDirectory) {
        this.rootDirectory = Path.of(rootDirectory);
        try {
            Files.createDirectories(this.rootDirectory);
        } catch (IOException e) {
            throw new UncheckedIOException("não foi possível preparar o diretório de armazenamento de importações", e);
        }
    }

    @Override
    public String store(String storageKeyHint, byte[] content) {
        String key = storageKeyHint + ".csv";
        try {
            Files.write(rootDirectory.resolve(key), content);
        } catch (IOException e) {
            throw new UncheckedIOException("falha ao gravar arquivo de importação", e);
        }
        return key;
    }

    @Override
    public byte[] read(String storageKey) {
        try {
            return Files.readAllBytes(rootDirectory.resolve(storageKey));
        } catch (IOException e) {
            throw new UncheckedIOException("falha ao ler arquivo de importação", e);
        }
    }
}
