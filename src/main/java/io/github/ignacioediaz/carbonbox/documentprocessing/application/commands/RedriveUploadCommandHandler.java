package io.github.ignacioediaz.carbonbox.documentprocessing.application.commands;

import io.github.ignacioediaz.carbonbox.documentprocessing.application.ports.MessagePublisher;
import io.github.ignacioediaz.carbonbox.documentprocessing.application.ports.UnitOfWork;
import io.github.ignacioediaz.carbonbox.documentprocessing.domain.Upload;
import io.github.ignacioediaz.carbonbox.documentprocessing.domain.UploadRepository;
import io.github.ignacioediaz.carbonbox.shared.domain.DomainError;
import io.github.ignacioediaz.carbonbox.shared.domain.Either;

import java.time.Clock;
import java.util.Optional;

/** FAILED -> PENDING y un mensaje nuevo en la cola. */
public class RedriveUploadCommandHandler {

    private final UploadRepository uploads;
    private final MessagePublisher publisher;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public RedriveUploadCommandHandler(UploadRepository uploads, MessagePublisher publisher, UnitOfWork unitOfWork,
                                       Clock clock) {
        this.uploads = uploads;
        this.publisher = publisher;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    public Either<DomainError, Upload> handle(RedriveUploadCommand command) {
        return unitOfWork.inTransaction(() -> {
            Optional<Upload> found = uploads.findById(command.uploadId());
            if (found.isEmpty()) {
                return Either.<DomainError, Upload>left(new DomainError.NotFound("No existe el upload " + command.uploadId()));
            }
            return found.get().backToPending(clock.instant()).map(u -> {
                uploads.save(u);
                publisher.publishProcessFile(u.id());
                return u;
            });
        });
    }
}
