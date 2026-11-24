package io.github.ignacioediaz.carbonbox.documentprocessing.infrastructure.persistence;

import io.github.ignacioediaz.carbonbox.documentprocessing.domain.ActivityData;
import io.github.ignacioediaz.carbonbox.documentprocessing.domain.ActivityDataRepository;
import io.github.ignacioediaz.carbonbox.documentprocessing.domain.RowError;
import io.github.ignacioediaz.carbonbox.documentprocessing.domain.RowErrorRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.util.List;
import java.util.UUID;

/**
 * Filas de actividad y errores por fila. Un CSV puede traer miles de filas: se insertan en lotes con JDBC
 * en vez de una entidad JPA por fila. Participan de la transaccion abierta por UnitOfWork.
 */
@Repository
public class JdbcActivityRepositories implements ActivityDataRepository, RowErrorRepository {

    private static final int BATCH = 500;
    private final JdbcTemplate jdbc;

    public JdbcActivityRepositories(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void replaceForUpload(UUID uploadId, List<ActivityData> rows) {
        jdbc.update("DELETE FROM activity_data WHERE upload_id = ?", uploadId);
        jdbc.batchUpdate("""
                INSERT INTO activity_data (id, upload_id, line_number, category, amount, unit, activity_date)
                VALUES (?, ?, ?, ?, ?, ?, ?)""", rows, BATCH, (ps, r) -> {
            ps.setObject(1, r.id());
            ps.setObject(2, r.uploadId());
            ps.setInt(3, r.rowNumber());
            ps.setString(4, r.category());
            ps.setBigDecimal(5, r.amount());
            ps.setString(6, r.unit());
            ps.setDate(7, Date.valueOf(r.date()));
        });
    }

    @Override
    public void replaceErrorsForUpload(UUID uploadId, List<RowError> errors) {
        jdbc.update("DELETE FROM row_errors WHERE upload_id = ?", uploadId);
        jdbc.batchUpdate("""
                INSERT INTO row_errors (upload_id, line_number, message, raw_line) VALUES (?, ?, ?, ?)""",
                errors, BATCH, (ps, e) -> {
                    ps.setObject(1, e.uploadId());
                    ps.setInt(2, e.rowNumber());
                    ps.setString(3, e.message());
                    ps.setString(4, e.rawLine());
                });
    }
}
