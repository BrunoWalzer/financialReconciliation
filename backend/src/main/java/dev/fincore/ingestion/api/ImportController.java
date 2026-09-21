package dev.fincore.ingestion.api;

import dev.fincore.configuration.application.GetSourceUseCase;
import dev.fincore.identity.application.GetCurrentUserUseCase;
import dev.fincore.identity.domain.AppUser;
import dev.fincore.identity.domain.CurrentUser;
import dev.fincore.ingestion.application.DownloadImportFileUseCase;
import dev.fincore.ingestion.application.DownloadedFile;
import dev.fincore.ingestion.application.GetImportBatchUseCase;
import dev.fincore.ingestion.application.ImportFileCommand;
import dev.fincore.ingestion.application.ImportFileUseCase;
import dev.fincore.ingestion.application.ListImportBatchesUseCase;
import dev.fincore.ingestion.application.ListRejectedRecordsUseCase;
import dev.fincore.ingestion.application.RetryImportUseCase;
import dev.fincore.ingestion.domain.ImportBatch;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * {@code POST /imports} · {@code POST /imports/{id}/retry} · {@code GET /imports} ·
 * {@code GET /imports/{id}} · {@code GET /imports/{id}/rejected-records} ·
 * {@code GET /imports/{id}/file} (TDS 20.2). Upload assíncrono a partir do M8: a resposta
 * devolve {@code 202} com o lote ainda em {@code RECEIVED} e {@code Location} apontando
 * para {@code GET /imports/{id}} — o cliente faz polling desse endpoint para acompanhar o
 * processamento (sem WebSocket no MVP, TDS 17.4).
 */
@RestController
@RequestMapping("/imports")
public class ImportController {

    private static final int DEFAULT_SIZE = 25;
    private static final int MAX_SIZE = 100;

    private final ImportFileUseCase importFileUseCase;
    private final RetryImportUseCase retryImportUseCase;
    private final GetImportBatchUseCase getImportBatchUseCase;
    private final ListImportBatchesUseCase listImportBatchesUseCase;
    private final ListRejectedRecordsUseCase listRejectedRecordsUseCase;
    private final DownloadImportFileUseCase downloadImportFileUseCase;
    private final GetCurrentUserUseCase getCurrentUserUseCase;
    private final GetSourceUseCase getSourceUseCase;

    public ImportController(
            ImportFileUseCase importFileUseCase,
            RetryImportUseCase retryImportUseCase,
            GetImportBatchUseCase getImportBatchUseCase,
            ListImportBatchesUseCase listImportBatchesUseCase,
            ListRejectedRecordsUseCase listRejectedRecordsUseCase,
            DownloadImportFileUseCase downloadImportFileUseCase,
            GetCurrentUserUseCase getCurrentUserUseCase,
            GetSourceUseCase getSourceUseCase) {
        this.importFileUseCase = importFileUseCase;
        this.retryImportUseCase = retryImportUseCase;
        this.getImportBatchUseCase = getImportBatchUseCase;
        this.listImportBatchesUseCase = listImportBatchesUseCase;
        this.listRejectedRecordsUseCase = listRejectedRecordsUseCase;
        this.downloadImportFileUseCase = downloadImportFileUseCase;
        this.getCurrentUserUseCase = getCurrentUserUseCase;
        this.getSourceUseCase = getSourceUseCase;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportBatchResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam String sourceCode,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate referenceDate,
            @RequestParam(required = false) UUID reimportOfId,
            @RequestParam(required = false) String reimportReason,
            @AuthenticationPrincipal CurrentUser currentUser,
            UriComponentsBuilder uriBuilder) {

        if (file.isEmpty() && file.getOriginalFilename() == null) {
            throw new IllegalArgumentException("file é obrigatório");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("nome de arquivo é obrigatório");
        }

        AppUser actor = getCurrentUserUseCase.execute(currentUser);
        ImportFileCommand command = new ImportFileCommand(
                sourceCode, originalFilename, readBytes(file), referenceDate, reimportOfId, reimportReason);

        ImportBatch batch = importFileUseCase.execute(command, actor.id(), actor.email());
        URI location = uriBuilder.replacePath("/imports/{id}").buildAndExpand(batch.id()).toUri();
        return ResponseEntity.accepted().location(location).body(toResponse(batch));
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<ImportBatchResponse> retry(
            @PathVariable UUID id, @AuthenticationPrincipal CurrentUser currentUser, UriComponentsBuilder uriBuilder) {
        AppUser actor = getCurrentUserUseCase.execute(currentUser);
        ImportBatch batch = retryImportUseCase.execute(id, actor.id(), actor.email());
        URI location = uriBuilder.replacePath("/imports/{id}").buildAndExpand(batch.id()).toUri();
        return ResponseEntity.accepted().location(location).body(toResponse(batch));
    }

    @GetMapping
    public PageResponse<ImportBatchResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_SIZE) int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_SIZE));
        Page<ImportBatch> batches = listImportBatchesUseCase.execute(pageable);
        return PageResponse.of(batches.map(this::toResponse));
    }

    @GetMapping("/{id}")
    public ImportBatchResponse get(@PathVariable UUID id) {
        return toResponse(getImportBatchUseCase.execute(id));
    }

    @GetMapping("/{id}/rejected-records")
    public PageResponse<RejectedRecordResponse> rejectedRecords(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_SIZE) int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_SIZE));
        return PageResponse.of(listRejectedRecordsUseCase.execute(id, pageable).map(RejectedRecordResponse::from));
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<byte[]> downloadFile(@PathVariable UUID id, @AuthenticationPrincipal CurrentUser currentUser) {
        AppUser actor = getCurrentUserUseCase.execute(currentUser);
        DownloadedFile file = downloadImportFileUseCase.execute(id, actor.id(), actor.email());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.filename()).build().toString())
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(file.content());
    }

    private ImportBatchResponse toResponse(ImportBatch batch) {
        String sourceCode = getSourceUseCase.execute(batch.sourceId()).code();
        return ImportBatchResponse.from(batch, sourceCode);
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("falha ao ler o arquivo enviado", e);
        }
    }
}
