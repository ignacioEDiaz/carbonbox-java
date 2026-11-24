package io.github.ignacioediaz.carbonbox.documentprocessing.domain;

import io.github.ignacioediaz.carbonbox.shared.domain.DomainError;
import io.github.ignacioediaz.carbonbox.shared.domain.Either;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Aggregate root: un archivo subido y el estado de su procesamiento. */
public final class Upload {

    private final UUID id;
    private final FileName fileName;
    private final String storageKey;
    private final IdempotencyKey idempotencyKey;
    private final Instant uploadedAt;
    private UploadStatus status;
    private int processedRows;
    private int errorRows;
    private String failureReason;
    private Instant updatedAt;

    private Upload(UUID id, FileName fileName, String storageKey, IdempotencyKey idempotencyKey, Instant uploadedAt,
                   UploadStatus status, int processedRows, int errorRows, String failureReason, Instant updatedAt) {
        this.id = Objects.requireNonNull(id);
        this.fileName = Objects.requireNonNull(fileName);
        this.storageKey = Objects.requireNonNull(storageKey);
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey);
        this.uploadedAt = Objects.requireNonNull(uploadedAt);
        this.status = Objects.requireNonNull(status);
        this.processedRows = processedRows;
        this.errorRows = errorRows;
        this.failureReason = failureReason;
        this.updatedAt = updatedAt;
    }

    public static Upload create(UUID id, FileName fileName, String storageKey, IdempotencyKey key, Instant now) {
        return new Upload(id, fileName, storageKey, key, now, UploadStatus.PENDING, 0, 0, null, now);
    }

    /** Reconstruye el agregado desde la persistencia, sin volver a validar transiciones. */
    public static Upload restore(UUID id, FileName fileName, String storageKey, IdempotencyKey key, Instant uploadedAt,
                                 UploadStatus status, int processedRows, int errorRows, String failureReason,
                                 Instant updatedAt) {
        return new Upload(id, fileName, storageKey, key, uploadedAt, status, processedRows, errorRows, failureReason,
                updatedAt);
    }

    public Either<DomainError, Upload> startProcessing(Instant now) {
        return moveTo(UploadStatus.PROCESSING, now);
    }

    public Either<DomainError, Upload> complete(int processedRows, int errorRows, Instant now) {
        if (processedRows < 0 || errorRows < 0) {
            return Either.left(new DomainError.Validation("La cantidad de filas no puede ser negativa"));
        }
        return moveTo(UploadStatus.COMPLETED, now).map(u -> {
            this.processedRows = processedRows;
            this.errorRows = errorRows;
            this.failureReason = null;
            return u;
        });
    }

    public Either<DomainError, Upload> fail(String reason, Instant now) {
        return moveTo(UploadStatus.FAILED, now).map(u -> {
            this.failureReason = reason;
            return u;
        });
    }

    /** Un intento fallo por algo transitorio: vuelve a la cola. */
    public Either<DomainError, Upload> backToPending(Instant now) {
        return moveTo(UploadStatus.PENDING, now);
    }

    private Either<DomainError, Upload> moveTo(UploadStatus next, Instant now) {
        if (!status.canTransitionTo(next)) {
            return Either.left(new DomainError.Conflict(
                    "El upload " + id + " no puede pasar de " + status + " a " + next));
        }
        this.status = next;
        this.updatedAt = now;
        if (next == UploadStatus.PENDING) {
            this.failureReason = null;
        }
        return Either.right(this);
    }

    public UUID id() { return id; }
    public FileName fileName() { return fileName; }
    public String storageKey() { return storageKey; }
    public IdempotencyKey idempotencyKey() { return idempotencyKey; }
    public Instant uploadedAt() { return uploadedAt; }
    public UploadStatus status() { return status; }
    public int processedRows() { return processedRows; }
    public int errorRows() { return errorRows; }
    public String failureReason() { return failureReason; }
    public Instant updatedAt() { return updatedAt; }
}
