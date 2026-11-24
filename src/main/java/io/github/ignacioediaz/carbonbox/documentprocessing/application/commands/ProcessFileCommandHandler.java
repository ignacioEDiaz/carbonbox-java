package io.github.ignacioediaz.carbonbox.documentprocessing.application.commands;

import io.github.ignacioediaz.carbonbox.documentprocessing.application.ports.FileStorage;
import io.github.ignacioediaz.carbonbox.documentprocessing.application.ports.UnitOfWork;
import io.github.ignacioediaz.carbonbox.documentprocessing.application.services.CsvParser;
import io.github.ignacioediaz.carbonbox.documentprocessing.domain.ActivityDataRepository;
import io.github.ignacioediaz.carbonbox.documentprocessing.domain.RowErrorRepository;
import io.github.ignacioediaz.carbonbox.documentprocessing.domain.Upload;
import io.github.ignacioediaz.carbonbox.documentprocessing.domain.UploadRepository;
import io.github.ignacioediaz.carbonbox.documentprocessing.domain.UploadStatus;
import io.github.ignacioediaz.carbonbox.shared.domain.DomainError;
import io.github.ignacioediaz.carbonbox.shared.domain.Either;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.Optional;

/**
 * Lo ejecuta el worker por cada mensaje. Es idempotente: la cola entrega "al menos una vez", asi que
 * un mensaje repetido de un upload ya terminado no hace nada, y uno a medio procesar reemplaza sus filas.
 * Errores de negocio (archivo invalido) -> el upload queda FAILED. Excepciones (disco, base) -> el worker reintenta.
 */
public class ProcessFileCommandHandler {

    private final UploadRepository uploads;
    private final ActivityDataRepository activity;
    private final RowErrorRepository rowErrors;
    private final FileStorage storage;
    private final UnitOfWork unitOfWork;
    private final CsvParser parser;
    private final Clock clock;

    public ProcessFileCommandHandler(UploadRepository uploads, ActivityDataRepository activity,
                                     RowErrorRepository rowErrors, FileStorage storage, UnitOfWork unitOfWork,
                                     CsvParser parser, Clock clock) {
        this.uploads = uploads;
        this.activity = activity;
        this.rowErrors = rowErrors;
        this.storage = storage;
        this.unitOfWork = unitOfWork;
        this.parser = parser;
        this.clock = clock;
    }

    public Either<DomainError, UploadStatus> handle(ProcessFileCommand command) {
        Optional<Upload> found = uploads.findById(command.uploadId());
        if (found.isEmpty()) {
            return Either.left(new DomainError.NotFound("No existe el upload " + command.uploadId()));
        }
        Upload upload = found.get();
        if (upload.status().isFinal()) {
            return Either.right(upload.status()); // mensaje repetido: ya se proceso
        }
        if (upload.status() == UploadStatus.PENDING) {
            // se guarda aparte para que el frontend vea PROCESSING mientras dura el trabajo
            Either<DomainError, Upload> started = upload.startProcessing(clock.instant());
            if (started.isLeft()) {
                return Either.left(started.getLeft());
            }
            unitOfWork.inTransaction(() -> {
                uploads.save(upload);
                return null;
            });
        }

        byte[] content;
        try {
            content = storage.read(upload.storageKey());
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo del upload " + upload.id(), e);
        }

        return parser.parse(upload.id(), content).fold(
                invalid -> finish(upload, upload.fail(invalid.message(), clock.instant())),
                parsed -> unitOfWork.inTransaction(() -> {
                    activity.replaceForUpload(upload.id(), parsed.rows());
                    rowErrors.replaceErrorsForUpload(upload.id(), parsed.errors());
                    Either<DomainError, Upload> done = parsed.rows().isEmpty()
                            ? upload.fail(parsed.errors().isEmpty()
                                    ? "El archivo no tiene filas de datos"
                                    : "Ninguna fila es válida (" + parsed.errors().size() + " con errores)", clock.instant())
                            : upload.complete(parsed.rows().size(), parsed.errors().size(), clock.instant());
                    return finish(upload, done);
                }));
    }

    private Either<DomainError, UploadStatus> finish(Upload upload, Either<DomainError, Upload> transition) {
        return transition.map(u -> {
            unitOfWork.inTransaction(() -> {
                uploads.save(u);
                return null;
            });
            return u.status();
        });
    }
}
