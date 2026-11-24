package io.github.ignacioediaz.carbonbox.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "carbonbox")
public record CarbonBoxProperties(@DefaultValue Storage storage, @DefaultValue Upload upload,
                                  @DefaultValue Queue queue) {

    public record Storage(@DefaultValue("./data/uploads") String dir) {
    }

    public record Upload(@DefaultValue("5242880") long maxBytes) {
    }

    /**
     * visibilityTimeout: cuanto tiempo queda oculto un mensaje tomado antes de volver a la cola.
     * maxReceiveCount: intentos antes de mandarlo a la DLQ (como maxReceiveCount de una redrive policy de SQS).
     */
    public record Queue(@DefaultValue("5") int batchSize,
                        @DefaultValue("PT30S") Duration visibilityTimeout,
                        @DefaultValue("3") int maxReceiveCount,
                        @DefaultValue("PT2S") Duration backoffBase,
                        @DefaultValue("PT1M") Duration backoffMax) {
    }
}
