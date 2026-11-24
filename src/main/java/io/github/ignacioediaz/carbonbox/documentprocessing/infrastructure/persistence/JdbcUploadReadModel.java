package io.github.ignacioediaz.carbonbox.documentprocessing.infrastructure.persistence;

import io.github.ignacioediaz.carbonbox.documentprocessing.application.queries.ReadModels.ActivityView;
import io.github.ignacioediaz.carbonbox.documentprocessing.application.queries.ReadModels.MonthlyActivityView;
import io.github.ignacioediaz.carbonbox.documentprocessing.application.queries.ReadModels.PageView;
import io.github.ignacioediaz.carbonbox.documentprocessing.application.queries.ReadModels.RowErrorView;
import io.github.ignacioediaz.carbonbox.documentprocessing.application.queries.ReadModels.SummaryView;
import io.github.ignacioediaz.carbonbox.documentprocessing.application.queries.ReadModels.UploadReadModel;
import io.github.ignacioediaz.carbonbox.documentprocessing.application.queries.ReadModels.UploadView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Lado de lectura: SQL directo a proyecciones, sin cargar agregados. */
@Repository
public class JdbcUploadReadModel implements UploadReadModel {

    private static final String UPLOAD_COLUMNS =
            "id, file_name, status, uploaded_at, updated_at, processed_rows, error_rows, failure_reason";

    private static final RowMapper<UploadView> UPLOAD = (rs, n) -> new UploadView(
            rs.getObject("id", UUID.class), rs.getString("file_name"), rs.getString("status"),
            instant(rs.getObject("uploaded_at", OffsetDateTime.class)),
            instant(rs.getObject("updated_at", OffsetDateTime.class)),
            rs.getInt("processed_rows"), rs.getInt("error_rows"), rs.getString("failure_reason"));

    private final JdbcTemplate jdbc;

    public JdbcUploadReadModel(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<UploadView> findUpload(UUID id) {
        return jdbc.query("SELECT " + UPLOAD_COLUMNS + " FROM uploads WHERE id = ?", UPLOAD, id).stream().findFirst();
    }

    @Override
    public PageView<UploadView> listUploads(int page, int size) {
        long total = count("SELECT COUNT(*) FROM uploads");
        List<UploadView> items = jdbc.query("SELECT " + UPLOAD_COLUMNS
                + " FROM uploads ORDER BY uploaded_at DESC, id LIMIT ? OFFSET ?", UPLOAD, size, (page - 1) * size);
        return PageView.of(items, page, size, total);
    }

    @Override
    public PageView<ActivityView> listActivity(UUID uploadId, int page, int size) {
        long total = count("SELECT COUNT(*) FROM activity_data WHERE upload_id = ?", uploadId);
        List<ActivityView> items = jdbc.query("""
                SELECT line_number, category, amount, unit, activity_date FROM activity_data
                WHERE upload_id = ? ORDER BY line_number LIMIT ? OFFSET ?""",
                (rs, n) -> new ActivityView(rs.getInt("line_number"), rs.getString("category"),
                        rs.getBigDecimal("amount").stripTrailingZeros(), rs.getString("unit"),
                        rs.getDate("activity_date").toLocalDate()),
                uploadId, size, (page - 1) * size);
        return PageView.of(items, page, size, total);
    }

    @Override
    public List<RowErrorView> listRowErrors(UUID uploadId) {
        return jdbc.query("SELECT line_number, message, raw_line FROM row_errors WHERE upload_id = ? ORDER BY line_number",
                (rs, n) -> new RowErrorView(rs.getInt("line_number"), rs.getString("message"), rs.getString("raw_line")),
                uploadId);
    }

    @Override
    public SummaryView summary() {
        Map<String, Long> byStatus = new HashMap<>();
        jdbc.query("SELECT status, COUNT(*) AS c FROM uploads GROUP BY status",
                rs -> {
                    byStatus.put(rs.getString("status"), rs.getLong("c"));
                });
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        return new SummaryView(total, byStatus.getOrDefault("PENDING", 0L), byStatus.getOrDefault("PROCESSING", 0L),
                byStatus.getOrDefault("COMPLETED", 0L), byStatus.getOrDefault("FAILED", 0L));
    }

    @Override
    public List<MonthlyActivityView> activityByMonth() {
        // EXTRACT funciona igual en PostgreSQL y en H2; el "2026-03" se arma aca
        return jdbc.query("""
                SELECT EXTRACT(YEAR FROM a.activity_date) AS y, EXTRACT(MONTH FROM a.activity_date) AS m,
                       a.category, a.unit, SUM(a.amount) AS total, COUNT(*) AS c
                FROM activity_data a JOIN uploads u ON u.id = a.upload_id
                WHERE u.status = 'COMPLETED'
                GROUP BY EXTRACT(YEAR FROM a.activity_date), EXTRACT(MONTH FROM a.activity_date), a.category, a.unit
                ORDER BY y, m, a.category, a.unit""",
                (rs, n) -> new MonthlyActivityView(String.format("%04d-%02d", rs.getInt("y"), rs.getInt("m")),
                        rs.getString("category"), rs.getString("unit"), rs.getBigDecimal("total").stripTrailingZeros(),
                        rs.getLong("c")));
    }

    private long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    private static Instant instant(OffsetDateTime t) {
        return t == null ? null : t.toInstant();
    }
}
