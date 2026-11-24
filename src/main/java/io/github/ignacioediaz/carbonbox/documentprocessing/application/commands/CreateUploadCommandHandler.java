package io.github.ignacioediaz.carbonbox.documentprocessing.application.commands;

import io.github.ignacioediaz.carbonbox.documentprocessing.application.ports.FileStorage;
import io.github.ignacioediaz.carbonbox.documentprocessing.application.ports.MessagePublisher;
import io.github.ignacioediaz.carbonbox.documentprocessing.application.ports.UnitOfWork;
import io.github.ignacioediaz.carbonbox.documentprocessing.domain.CsvHeader;
import io.github.ignacioediaz.carbonbox.documentprocessing.domain.FileName;
import io.github.ignacioediaz.carbonbox.documentprocessing.domain.IdempotencyKey;
import io.github.ignacioediaz.carbonbox.documentprocessing.domain.Upload;
import io.github.ignacioediaz.carbonbox.documentprocessing.domain.UploadRepository;
import io.github.ignacioediaz.carbonbox.shared.domain.DomainError;
import io.github.ignacioediaz.carbonbox.shared.domain.Either;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/**
 * Valida el archivo, lo guarda, crea el upload en PENDING y encola su procesamiento.
 * Responde enseguida: el CSV se procesa despues, en el worker.
 */
public class CreateUploadCommandHandler {

    public record Result(UUID uploadId, boolean created) {
    }

    private final UploadRepository uploads;
    private final FileStorage storage;
    private final MessagePublisher publisher;
    private final UnitOfWork unitOfWork;
    private final Clock clock;
    private final long maxBytes;

    public CreateUploadCommandHandler(UploadRepository uploads, FileStorage storage, MessagePublisher publisher,
                                      UnitOfWork unitOfWork, Clock clock, long maxBytes) {
        this.uploads = uploads;
        this.storage = storage;
        this.publisher = publisher;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
        this.maxBytes = maxBytes;
    }

    public Either<DomainError, Result> handle(CreateUploadCommand command) {
        byte[] content = command.content() == null ? new byte[0] : command.content();
        if (content.length == 0) {
            return Either.left(new DomainError.Validation("El archivo está vacío"));
        }
        if (content.length > maxBytes) {
            return Either.left(new DomainError.Validation("El archivo supera el máximo de " + maxBytes / 1024 + " KB"));
        }
        Either<DomainError, FileName> fileName = FileName.of(command.fileName());
        if (fileName.isLeft()) {
            return Either.left(fileName.getLeft());
        }
        // el encabezado se valida al subir: un archivo que no es de actividad se rechaza al instante
        Either<DomainError, ?> header = CsvHeader.fromContent(content);
        if (header.isLeft()) {
            return Either.left(header.getLeft());
        }
        Either<DomainError, IdempotencyKey> key = command.idempotencyKey() == null
                ? Either.right(IdempotencyKey.fromContent(content))
                : IdempotencyKey.fromHeader(command.idempotencyKey());
        if (key.isLeft()) {
            return Either.left(key.getLeft());
        }

        Optional<Upload> existing = uploads.findByIdempotencyKey(key.getRight());
        if (existing.isPresent()) {
            return Either.right(new Result(existing.get().id(), false));
        }

        UUID id = UUID.randomUUID();
        String storageKey = id + ".csv";
        try {
            storage.save(storageKey, content);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar el archivo", e);
        }
        Upload upload = Upload.create(id, fileName.getRight(), storageKey, key.getRight(), clock.instant());
        // upload y mensaje en la misma transaccion: o quedan los dos o ninguno (la cola vive en la misma base)
        unitOfWork.inTransaction(() -> {
            uploads.save(upload);
            publisher.publishProcessFile(id);
            return null;
        });
        return Either.right(new Result(id, true));
    }
}
