package io.github.ignacioediaz.carbonbox.documentprocessing.application.commands;

import java.util.UUID;

/**
 * Un intento de procesar fallo con una excepcion.
 * finalFailure = true: se agotaron los reintentos y el mensaje paso a la DLQ.
 */
public record RecordProcessingFailureCommand(UUID uploadId, String reason, boolean finalFailure) {
}
