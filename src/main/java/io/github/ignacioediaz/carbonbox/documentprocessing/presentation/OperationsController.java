package io.github.ignacioediaz.carbonbox.documentprocessing.presentation;

import io.github.ignacioediaz.carbonbox.documentprocessing.application.queries.UploadQueryHandlers;
import io.github.ignacioediaz.carbonbox.documentprocessing.infrastructure.messaging.DatabaseMessageQueue;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** Reportes y operacion: actividad por mes, estado de la cola, DLQ y health check. */
@RestController
@RequestMapping("/api")
public class OperationsController {

    private final UploadQueryHandlers queries;
    private final DatabaseMessageQueue queue;
    private final JdbcTemplate jdbc;

    public OperationsController(UploadQueryHandlers queries, DatabaseMessageQueue queue, JdbcTemplate jdbc) {
        this.queries = queries;
        this.queue = queue;
        this.jdbc = jdbc;
    }

    @GetMapping("/activity/by-month")
    public ResponseEntity<?> activityByMonth() {
        return ResponseEntity.ok(queries.activityByMonth());
    }

    @GetMapping("/queue/stats")
    public ResponseEntity<?> queueStats() {
        return ResponseEntity.ok(queue.stats());
    }

    @GetMapping("/queue/dead-letters")
    public ResponseEntity<?> deadLetters() {
        return ResponseEntity.ok(queue.deadLetters());
    }

    /** 200 si la base responde; 503 si no (para el health check del contenedor). */
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        boolean dbUp;
        try {
            dbUp = Integer.valueOf(1).equals(jdbc.queryForObject("SELECT 1", Integer.class));
        } catch (RuntimeException e) {
            dbUp = false;
        }
        body.put("status", dbUp ? "UP" : "DOWN");
        body.put("database", dbUp ? "UP" : "DOWN");
        return ResponseEntity.status(dbUp ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
}
