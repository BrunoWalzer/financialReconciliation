-- V6 — Fecha a chave estrangeira diferida em V4 (FD-9 do Implementation Plan): agora que
-- import_batch existe (V5), financial_record.import_batch_id ganha a FK que não podia
-- existir antes, sem violar a ordem forward-only das migrations.

ALTER TABLE financial_record
    ADD CONSTRAINT fk_financial_record_import_batch
    FOREIGN KEY (import_batch_id) REFERENCES import_batch (id);
