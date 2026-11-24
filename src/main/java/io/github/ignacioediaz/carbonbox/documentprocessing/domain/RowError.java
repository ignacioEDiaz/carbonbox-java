package io.github.ignacioediaz.carbonbox.documentprocessing.domain;

import java.util.UUID;

/** Una fila invalida del CSV y por que. El numero de fila cuenta el encabezado como fila 1. */
public record RowError(UUID uploadId, int rowNumber, String message, String rawLine) {
}
