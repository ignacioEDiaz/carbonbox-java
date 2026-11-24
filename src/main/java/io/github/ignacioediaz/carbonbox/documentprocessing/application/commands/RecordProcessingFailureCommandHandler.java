package io.github.ignacioediaz.carbonbox.documentprocessing.application.commands;

import io.github.ignacioediaz.carbonbox.documentprocessing.application.ports.UnitOfWork;
import io.github.ignacioediaz.carbonbox.documentprocessing.domain.Upload;
import io.github.ignacioediaz.carbonbox.documentprocessing.domain.UploadRepository;
import io.github.ignacioediaz.carbonbox.documentprocessing.domain.UploadStatus;
import io.github.ignacioediaz.carbonbox.shared.domain.DomainError;
import io.github.ignacioediaz.carbonbox.shared.domain.Either;

import java.time.Clock;
import java.util.Optional;

/** Refleja en el upload lo que paso con su mensaje: vuelve a PENDING para reintentar, o FAILED si fue a la DLQ. */
public class RecordProcessingFailureCommandHandler {

    private final UploadRepository uploads;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public RecordProcessingFailureCommandHandler(UploadRepository uploads, UnitOfWork unitOfWork, Clock clock) {
        this.uploads = uploads;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    public Either<DomainError, UploadStatus> handle(RecordProcessingFailureCommand command) {
        return unitOfWork.inTransaction(() -> {
            Optional<Upload> found = uploads.findById(command.uploadId());
            if (found.isEmpty()) {
                return Either.<DomainError, UploadStatus>left(new DomainError.NotFound("No existe el upload " + command.uploadId()));
            }
            Upload upload = found.get();
            if (upload.status().isFinal()) {
                return Either.<DomainError, UploadStatus>right(upload.status());
            }
            Either<DomainError, Upload> next;
            if (command.finalFailure()) {
                next = upload.fail("No se pudo procesar después de varios intentos: " + command.reason(), clock.instant());
            } else if (upload.status() == UploadStatus.PROCESSING) {
                next = upload.backToPending(clock.instant());
            } else {
                next = Either.right(upload); // ya estaba PENDING
            }
            return next.map(u -> {
                uploads.save(u);
                return u.status();
            });
        });
    }
}
