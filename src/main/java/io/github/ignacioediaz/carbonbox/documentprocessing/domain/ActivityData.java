package io.github.ignacioediaz.carbonbox.documentprocessing.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Una fila valida del CSV: cuanto de que actividad, en que unidad y en que fecha. */
public record ActivityData(UUID id, UUID uploadId, int rowNumber, String category, BigDecimal amount, String unit,
                           LocalDate date) {
}
