package io.github.ignacioediaz.carbonbox.documentprocessing.application.commands;

import java.util.UUID;

/** Volver a encolar un upload que fallo (por ejemplo, despues de arreglar lo que lo hacia fallar). */
public record RedriveUploadCommand(UUID uploadId) {
}
