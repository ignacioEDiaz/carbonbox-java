package io.github.ignacioediaz.carbonbox.documentprocessing.domain;

import io.github.ignacioediaz.carbonbox.shared.domain.DomainError;
import io.github.ignacioediaz.carbonbox.shared.domain.Either;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Primera linea del CSV. La misma regla la usan la subida (rechaza al instante) y el parser,
 * asi nunca aceptan formatos distintos.
 */
public final class CsvHeader {

    public static final List<String> EXPECTED_COLUMNS = List.of("category", "amount", "unit", "date");
    private static final String EXPECTED = String.join(",", EXPECTED_COLUMNS);
    private static final int MAX_HEADER_BYTES = 1024;
    private static final int MAX_PREVIEW = 100;

    private CsvHeader() {
    }

    public static Either<DomainError, List<String>> fromContent(byte[] content) {
        String line = firstLine(content);
        String normalized = Arrays.stream(line.split(",", -1))
                .map(c -> c.trim().toLowerCase())
                .collect(Collectors.joining(","));
        if (!normalized.equals(EXPECTED)) {
            String preview = line.length() > MAX_PREVIEW ? line.substring(0, MAX_PREVIEW) + "…" : line;
            return Either.left(new DomainError.Validation(
                    "El encabezado debe ser \"" + EXPECTED + "\" y llegó \"" + preview + "\""));
        }
        return Either.right(EXPECTED_COLUMNS);
    }

    /** Un archivo binario sin saltos de linea no se recorre entero: el encabezado esta al principio. */
    static String firstLine(byte[] content) {
        int limit = Math.min(content.length, MAX_HEADER_BYTES);
        int end = 0;
        while (end < limit && content[end] != '\n') {
            end++;
        }
        String line = new String(content, 0, end, StandardCharsets.UTF_8);
        if (line.startsWith("﻿")) {
            line = line.substring(1); // BOM que agrega Excel
        }
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }
}
