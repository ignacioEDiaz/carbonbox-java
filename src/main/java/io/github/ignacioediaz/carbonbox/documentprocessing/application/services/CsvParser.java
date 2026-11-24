package io.github.ignacioediaz.carbonbox.documentprocessing.application.services;

import io.github.ignacioediaz.carbonbox.documentprocessing.domain.ActivityData;
import io.github.ignacioediaz.carbonbox.documentprocessing.domain.CsvHeader;
import io.github.ignacioediaz.carbonbox.documentprocessing.domain.RowError;
import io.github.ignacioediaz.carbonbox.shared.domain.DomainError;
import io.github.ignacioediaz.carbonbox.shared.domain.Either;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Lee el CSV "category,amount,unit,date". Una fila mala no frena el archivo: se guarda como error
 * con su numero de fila y el motivo, y se sigue con la siguiente.
 */
public class CsvParser {

    public record ParseResult(List<ActivityData> rows, List<RowError> errors) {
    }

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuu-MM-dd")
            .withResolverStyle(ResolverStyle.STRICT); // STRICT: 2026-02-30 es invalida
    private static final int MAX_RAW_LINE = 300;

    public Either<DomainError, ParseResult> parse(UUID uploadId, byte[] content) {
        Either<DomainError, List<String>> header = CsvHeader.fromContent(content);
        if (header.isLeft()) {
            return Either.left(header.getLeft());
        }
        String text = new String(content, StandardCharsets.UTF_8);
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        String[] lines = text.split("\r?\n", -1);
        List<ActivityData> rows = new ArrayList<>();
        List<RowError> errors = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) {
                continue;
            }
            int rowNumber = i + 1;
            String error = parseRow(uploadId, rowNumber, line, rows);
            if (error != null) {
                String raw = line.length() > MAX_RAW_LINE ? line.substring(0, MAX_RAW_LINE) + "…" : line;
                errors.add(new RowError(uploadId, rowNumber, error, raw));
            }
        }
        return Either.right(new ParseResult(rows, errors));
    }

    /** Agrega la fila a rows si es valida; si no, devuelve el motivo. */
    private String parseRow(UUID uploadId, int rowNumber, String line, List<ActivityData> rows) {
        List<String> cells;
        try {
            cells = split(line);
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
        if (cells.size() != CsvHeader.EXPECTED_COLUMNS.size()) {
            return "Tiene " + cells.size() + " columnas y se esperan " + CsvHeader.EXPECTED_COLUMNS.size();
        }
        String category = cells.get(0).trim();
        String amountText = cells.get(1).trim();
        String unit = cells.get(2).trim();
        String dateText = cells.get(3).trim();

        List<String> problems = new ArrayList<>();
        if (category.isEmpty()) {
            problems.add("category está vacío");
        }
        BigDecimal amount = null;
        try {
            amount = new BigDecimal(amountText);
            if (amount.signum() <= 0) {
                problems.add("amount debe ser un número positivo y llegó " + amountText);
            }
        } catch (NumberFormatException e) {
            problems.add("amount no es un número: \"" + amountText + "\"");
        }
        if (unit.isEmpty()) {
            problems.add("unit está vacío");
        }
        LocalDate date = null;
        try {
            date = LocalDate.parse(dateText, DATE);
        } catch (DateTimeParseException e) {
            problems.add("date debe ser una fecha válida YYYY-MM-DD y llegó \"" + dateText + "\"");
        }
        if (!problems.isEmpty()) {
            return String.join("; ", problems);
        }
        rows.add(new ActivityData(UUID.randomUUID(), uploadId, rowNumber, category, amount, unit, date));
        return null;
    }

    /** Separa por comas respetando campos entre comillas ("Gas, natural") y comillas dobles escapadas. */
    static List<String> split(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                cells.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (quoted) {
            throw new IllegalArgumentException("Hay comillas sin cerrar");
        }
        cells.add(current.toString());
        return cells;
    }
}
