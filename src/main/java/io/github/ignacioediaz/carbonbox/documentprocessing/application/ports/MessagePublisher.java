package io.github.ignacioediaz.carbonbox.documentprocessing.application.ports;

import java.util.UUID;

/** Encola el pedido de procesar un upload. */
public interface MessagePublisher {

    void publishProcessFile(UUID uploadId);
}
