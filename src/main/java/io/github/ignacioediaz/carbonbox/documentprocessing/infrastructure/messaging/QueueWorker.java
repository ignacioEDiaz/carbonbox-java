package io.github.ignacioediaz.carbonbox.documentprocessing.infrastructure.messaging;

import io.github.ignacioediaz.carbonbox.config.CarbonBoxProperties;
import io.github.ignacioediaz.carbonbox.documentprocessing.application.commands.ProcessFileCommand;
import io.github.ignacioediaz.carbonbox.documentprocessing.application.commands.ProcessFileCommandHandler;
import io.github.ignacioediaz.carbonbox.documentprocessing.application.commands.RecordProcessingFailureCommand;
import io.github.ignacioediaz.carbonbox.documentprocessing.application.commands.RecordProcessingFailureCommandHandler;
import io.github.ignacioediaz.carbonbox.documentprocessing.domain.UploadStatus;
import io.github.ignacioediaz.carbonbox.shared.domain.Either;
import io.github.ignacioediaz.carbonbox.shared.domain.DomainError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;

/**
 * Consume la cola. Por cada mensaje ejecuta ProcessFileCommand:
 * - sale bien, o con un error de negocio (el upload ya quedo FAILED o no existe): se confirma el mensaje.
 * - tira una excepcion (algo transitorio): se reintenta con backoff exponencial; al agotar los intentos
 *   el mensaje va a la DLQ y el upload queda FAILED con el motivo.
 */
public class QueueWorker {

    private static final Logger log = LoggerFactory.getLogger(QueueWorker.class);

    private final DatabaseMessageQueue queue;
    private final ProcessFileCommandHandler processHandler;
    private final RecordProcessingFailureCommandHandler failureHandler;
    private final CarbonBoxProperties.Queue config;

    public QueueWorker(DatabaseMessageQueue queue, ProcessFileCommandHandler processHandler,
                       RecordProcessingFailureCommandHandler failureHandler, CarbonBoxProperties.Queue config) {
        this.queue = queue;
        this.processHandler = processHandler;
        this.failureHandler = failureHandler;
        this.config = config;
    }

    /** Toma un lote de mensajes y los procesa. Devuelve cuantos tomo. */
    public int pollOnce() {
        List<DatabaseMessageQueue.ReceivedMessage> messages = queue.receive(config.batchSize(), config.visibilityTimeout());
        messages.forEach(this::process);
        return messages.size();
    }

    private void process(DatabaseMessageQueue.ReceivedMessage message) {
        try {
            Either<DomainError, UploadStatus> result = processHandler.handle(new ProcessFileCommand(message.uploadId()));
            result.fold(
                    error -> {
                        log.warn("Upload {}: {} (no se reintenta)", message.uploadId(), error.message());
                        return null;
                    },
                    status -> {
                        log.info("Upload {} procesado: {}", message.uploadId(), status);
                        return null;
                    });
            queue.acknowledge(message.id());
        } catch (RuntimeException e) {
            String reason = e.getClass().getSimpleName() + ": " + e.getMessage()
                    + (e.getCause() != null && e.getCause() != e ? " (" + e.getCause().getMessage() + ")" : "");
            if (message.receiveCount() >= config.maxReceiveCount()) {
                log.error("Upload {}: intento {} falló, va a la DLQ. {}", message.uploadId(), message.receiveCount(), reason);
                queue.deadLetter(message.id(), reason);
                failureHandler.handle(new RecordProcessingFailureCommand(message.uploadId(), reason, true));
            } else {
                Duration delay = backoff(message.receiveCount());
                log.warn("Upload {}: intento {} falló, se reintenta en {} ms. {}", message.uploadId(),
                        message.receiveCount(), delay.toMillis(), reason);
                queue.retryLater(message.id(), delay, reason);
                failureHandler.handle(new RecordProcessingFailureCommand(message.uploadId(), reason, false));
            }
        }
    }

    /** 1er reintento: base; despues el doble cada vez, con tope. */
    Duration backoff(int attempt) {
        long base = config.backoffBase().toMillis();
        long millis = base * (1L << Math.min(Math.max(attempt - 1, 0), 20));
        return Duration.ofMillis(Math.min(millis, config.backoffMax().toMillis()));
    }
}
