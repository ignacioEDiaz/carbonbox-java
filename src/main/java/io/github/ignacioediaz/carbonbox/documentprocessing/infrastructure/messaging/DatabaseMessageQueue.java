package io.github.ignacioediaz.carbonbox.documentprocessing.infrastructure.messaging;

import io.github.ignacioediaz.carbonbox.documentprocessing.application.ports.MessagePublisher;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cola sobre una tabla, con la misma semantica que SQS:
 * - receive: toma mensajes visibles y los oculta por un "visibility timeout". Si el worker se cae, el mensaje
 *   vuelve a ser visible solo y otro worker lo toma (entrega "al menos una vez").
 * - Cada recepcion suma receive_count; al llegar al maximo el mensaje va a la DLQ (status DEAD).
 * - Tomar un mensaje es un UPDATE condicional: si dos workers compiten, solo a uno le devuelve 1 fila.
 * Como vive en la misma base que los uploads, publicar es parte de la transaccion del comando (outbox).
 */
public class DatabaseMessageQueue implements MessagePublisher {

    public record ReceivedMessage(UUID id, UUID uploadId, int receiveCount) {
    }

    public record DeadLetter(UUID id, UUID uploadId, int receiveCount, String lastError, Instant createdAt) {
    }

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public DatabaseMessageQueue(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public void publishProcessFile(UUID uploadId) {
        OffsetDateTime now = now();
        // un mensaje nuevo para el mismo upload (redrive) reemplaza a los que estaban en la DLQ
        markDeadLettersRedriven(uploadId);
        jdbc.update("""
                INSERT INTO queue_messages (id, upload_id, status, receive_count, visible_at, created_at)
                VALUES (?, ?, 'QUEUED', 0, ?, ?)""", UUID.randomUUID(), uploadId, now, now);
    }

    public List<ReceivedMessage> receive(int max, Duration visibilityTimeout) {
        OffsetDateTime now = now();
        List<UUID> candidates = jdbc.queryForList("""
                SELECT id FROM queue_messages WHERE status = 'QUEUED' AND visible_at <= ?
                ORDER BY visible_at, created_at LIMIT ?""", UUID.class, now, max);
        List<ReceivedMessage> received = new ArrayList<>();
        for (UUID id : candidates) {
            int claimed = jdbc.update("""
                    UPDATE queue_messages SET visible_at = ?, receive_count = receive_count + 1
                    WHERE id = ? AND status = 'QUEUED' AND visible_at <= ?""",
                    now.plus(visibilityTimeout), id, now);
            if (claimed == 1) {
                jdbc.query("SELECT upload_id, receive_count FROM queue_messages WHERE id = ?",
                        rs -> {
                            received.add(new ReceivedMessage(id, rs.getObject("upload_id", UUID.class),
                                    rs.getInt("receive_count")));
                        }, id);
            }
        }
        return received;
    }

    /** Procesado: equivale a borrar el mensaje en SQS. Se guarda como DONE para poder auditarlo. */
    public void acknowledge(UUID messageId) {
        jdbc.update("UPDATE queue_messages SET status = 'DONE', last_error = NULL WHERE id = ?", messageId);
    }

    public void retryLater(UUID messageId, Duration delay, String error) {
        jdbc.update("UPDATE queue_messages SET visible_at = ?, last_error = ? WHERE id = ?",
                now().plus(delay), truncate(error), messageId);
    }

    public void deadLetter(UUID messageId, String error) {
        jdbc.update("UPDATE queue_messages SET status = 'DEAD', last_error = ? WHERE id = ?", truncate(error), messageId);
    }

    /** Al reenviar un upload, sus mensajes muertos quedan marcados para no reenviarlos dos veces. */
    public void markDeadLettersRedriven(UUID uploadId) {
        jdbc.update("UPDATE queue_messages SET status = 'REDRIVEN' WHERE upload_id = ? AND status = 'DEAD'", uploadId);
    }

    public List<DeadLetter> deadLetters() {
        return jdbc.query("""
                SELECT id, upload_id, receive_count, last_error, created_at FROM queue_messages
                WHERE status = 'DEAD' ORDER BY created_at DESC""",
                (rs, n) -> new DeadLetter(rs.getObject("id", UUID.class), rs.getObject("upload_id", UUID.class),
                        rs.getInt("receive_count"), rs.getString("last_error"),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant()));
    }

    /** Mensajes por estado; "in_flight" son los QUEUED que un worker tiene tomados ahora. */
    public Map<String, Long> stats() {
        Map<String, Long> stats = new LinkedHashMap<>();
        for (String s : List.of("QUEUED", "DONE", "DEAD", "REDRIVEN")) {
            stats.put(s.toLowerCase(), 0L);
        }
        jdbc.query("SELECT status, COUNT(*) AS c FROM queue_messages GROUP BY status",
                rs -> {
                    stats.put(rs.getString("status").toLowerCase(), rs.getLong("c"));
                });
        Long inFlight = jdbc.queryForObject("""
                SELECT COUNT(*) FROM queue_messages WHERE status = 'QUEUED' AND receive_count > 0 AND visible_at > ?""",
                Long.class, now());
        stats.put("in_flight", inFlight == null ? 0 : inFlight);
        return stats;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static String truncate(String s) {
        return s == null || s.length() <= 1000 ? s : s.substring(0, 1000);
    }
}
