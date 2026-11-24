package io.github.ignacioediaz.carbonbox.documentprocessing.application.queries;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Vistas del lado de lectura (CQRS). No son el agregado: son proyecciones armadas para lo que muestra
 * el frontend, leidas con SQL directo en vez de pasar por el modelo de dominio.
 */
public final class ReadModels {

    private ReadModels() {
    }

    public record UploadView(UUID id, String fileName, String status, Instant uploadedAt, Instant updatedAt,
                             int processedRows, int errorRows, String failureReason) {
    }

    public record ActivityView(int rowNumber, String category, BigDecimal amount, String unit, LocalDate date) {
    }

    public record RowErrorView(int rowNumber, String message, String rawLine) {
    }

    public record SummaryView(long total, long pending, long processing, long completed, long failed) {
    }

    public record MonthlyActivityView(String month, String category, String unit, BigDecimal total, long rows) {
    }

    public record PageView<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

        public static <T> PageView<T> of(List<T> items, int page, int size, long totalItems) {
            int pages = size == 0 ? 0 : (int) Math.ceil((double) totalItems / size);
            return new PageView<>(items, page, size, totalItems, pages);
        }
    }

    /** Puerto de lectura: lo implementa la infraestructura con SQL. */
    public interface UploadReadModel {

        java.util.Optional<UploadView> findUpload(UUID id);

        PageView<UploadView> listUploads(int page, int size);

        PageView<ActivityView> listActivity(UUID uploadId, int page, int size);

        List<RowErrorView> listRowErrors(UUID uploadId);

        SummaryView summary();

        List<MonthlyActivityView> activityByMonth();
    }
}
