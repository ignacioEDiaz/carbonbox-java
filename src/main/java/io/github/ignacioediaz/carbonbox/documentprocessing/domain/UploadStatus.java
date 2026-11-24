package io.github.ignacioediaz.carbonbox.documentprocessing.domain;

import java.util.Set;

/**
 * Ciclo de vida de un upload.
 * PENDING -> PROCESSING -> COMPLETED | FAILED
 * PROCESSING -> PENDING cuando un intento falla y el mensaje se reintenta.
 * PENDING -> FAILED cuando el mensaje se agota los reintentos y va a la DLQ.
 * FAILED -> PENDING cuando se reenvia (redrive) un mensaje de la DLQ.
 */
public enum UploadStatus {
    PENDING, PROCESSING, COMPLETED, FAILED;

    public boolean canTransitionTo(UploadStatus next) {
        return switch (this) {
            case PENDING -> Set.of(PROCESSING, FAILED).contains(next);
            case PROCESSING -> Set.of(COMPLETED, FAILED, PENDING).contains(next);
            case FAILED -> next == PENDING;
            case COMPLETED -> false;
        };
    }

    public boolean isFinal() {
        return this == COMPLETED || this == FAILED;
    }
}
