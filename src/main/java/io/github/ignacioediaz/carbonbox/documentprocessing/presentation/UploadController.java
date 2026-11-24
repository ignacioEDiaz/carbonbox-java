package io.github.ignacioediaz.carbonbox.documentprocessing.presentation;

import io.github.ignacioediaz.carbonbox.documentprocessing.application.commands.CreateUploadCommand;
import io.github.ignacioediaz.carbonbox.documentprocessing.application.commands.CreateUploadCommandHandler;
import io.github.ignacioediaz.carbonbox.documentprocessing.application.commands.RedriveUploadCommand;
import io.github.ignacioediaz.carbonbox.documentprocessing.application.commands.RedriveUploadCommandHandler;
import io.github.ignacioediaz.carbonbox.documentprocessing.application.queries.UploadQueryHandlers;
import io.github.ignacioediaz.carbonbox.documentprocessing.application.queries.UploadQueryHandlers.GetUploadByIdQuery;
import io.github.ignacioediaz.carbonbox.documentprocessing.application.queries.UploadQueryHandlers.GetUploadResultsQuery;
import io.github.ignacioediaz.carbonbox.documentprocessing.application.queries.UploadQueryHandlers.GetUploadRowErrorsQuery;
import io.github.ignacioediaz.carbonbox.documentprocessing.application.queries.UploadQueryHandlers.ListUploadsQuery;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/uploads")
public class UploadController {

    private final CreateUploadCommandHandler createUpload;
    private final RedriveUploadCommandHandler redrive;
    private final UploadQueryHandlers queries;

    public UploadController(CreateUploadCommandHandler createUpload, RedriveUploadCommandHandler redrive,
                            UploadQueryHandlers queries) {
        this.createUpload = createUpload;
        this.redrive = redrive;
        this.queries = queries;
    }

    /** 202: aceptado y encolado. 200: ya existia un upload con la misma clave (no se crea otro). */
    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<?> create(@RequestPart("file") MultipartFile file,
                                    @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey)
            throws IOException {
        return createUpload.handle(new CreateUploadCommand(file.getOriginalFilename(), file.getBytes(), idempotencyKey))
                .fold(ApiResponses::error, r -> {
                    Map<String, Object> body = Map.of("id", r.uploadId(), "created", r.created());
                    return r.created()
                            ? ResponseEntity.status(HttpStatus.ACCEPTED)
                                .location(URI.create("/api/uploads/" + r.uploadId())).body(body)
                            : ResponseEntity.ok(body);
                });
    }

    @GetMapping
    public ResponseEntity<?> list(@RequestParam(defaultValue = "1") int page,
                                  @RequestParam(defaultValue = "10") int size) {
        return ApiResponses.ok(queries.handle(new ListUploadsQuery(page, size)));
    }

    @GetMapping("/summary")
    public ResponseEntity<?> summary() {
        return ResponseEntity.ok(queries.summary());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable UUID id) {
        return ApiResponses.ok(queries.handle(new GetUploadByIdQuery(id)));
    }

    @GetMapping("/{id}/results")
    public ResponseEntity<?> results(@PathVariable UUID id, @RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "50") int size) {
        return ApiResponses.ok(queries.handle(new GetUploadResultsQuery(id, page, size)));
    }

    @GetMapping("/{id}/errors")
    public ResponseEntity<?> errors(@PathVariable UUID id) {
        return ApiResponses.ok(queries.handle(new GetUploadRowErrorsQuery(id)));
    }

    @PostMapping("/{id}/redrive")
    public ResponseEntity<?> redrive(@PathVariable UUID id) {
        return redrive.handle(new RedriveUploadCommand(id))
                .fold(ApiResponses::error, u -> ResponseEntity.accepted().body(Map.of("id", u.id(), "status", u.status())));
    }
}
