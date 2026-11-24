package io.github.ignacioediaz.carbonbox.documentprocessing.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Fila de la tabla uploads. Solo la usa el adaptador JPA; el dominio trabaja con Upload. */
@Entity
@Table(name = "uploads")
public class UploadEntity {

    @Id
    private UUID id;
    @Column(name = "file_name", nullable = false)
    private String fileName;
    @Column(name = "storage_key", nullable = false)
    private String storageKey;
    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;
    @Column(nullable = false)
    private String status;
    @Column(name = "processed_rows", nullable = false)
    private int processedRows;
    @Column(name = "error_rows", nullable = false)
    private int errorRows;
    @Column(name = "failure_reason")
    private String failureReason;
    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UploadEntity() {
    }

    public UploadEntity(UUID id, String fileName, String storageKey, String idempotencyKey, String status,
                        int processedRows, int errorRows, String failureReason, Instant uploadedAt, Instant updatedAt) {
        this.id = id;
        this.fileName = fileName;
        this.storageKey = storageKey;
        this.idempotencyKey = idempotencyKey;
        this.status = status;
        this.processedRows = processedRows;
        this.errorRows = errorRows;
        this.failureReason = failureReason;
        this.uploadedAt = uploadedAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public String getFileName() { return fileName; }
    public String getStorageKey() { return storageKey; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getStatus() { return status; }
    public int getProcessedRows() { return processedRows; }
    public int getErrorRows() { return errorRows; }
    public String getFailureReason() { return failureReason; }
    public Instant getUploadedAt() { return uploadedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
