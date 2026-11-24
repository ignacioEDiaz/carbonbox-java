package io.github.ignacioediaz.carbonbox.documentprocessing.infrastructure.messaging;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Hace correr el worker cada pocos milisegundos. En los tests se apaga y el worker se llama a mano. */
@Component
@ConditionalOnProperty(name = "carbonbox.worker.enabled", havingValue = "true", matchIfMissing = true)
public class ScheduledQueuePoller {

    private final QueueWorker worker;

    public ScheduledQueuePoller(QueueWorker worker) {
        this.worker = worker;
    }

    @Scheduled(fixedDelayString = "${carbonbox.worker.poll-interval:PT0.5S}")
    public void poll() {
        worker.pollOnce();
    }
}
