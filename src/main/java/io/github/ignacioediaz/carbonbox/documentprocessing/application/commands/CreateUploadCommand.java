package io.github.ignacioediaz.carbonbox.documentprocessing.application.commands;

/** Subir un CSV. idempotencyKey es opcional (header Idempotency-Key). */
public record CreateUploadCommand(String fileName, byte[] content, String idempotencyKey) {
}
