package io.github.ignacioediaz.carbonbox;

import io.github.ignacioediaz.carbonbox.documentprocessing.application.commands.CreateUploadCommand;
import io.github.ignacioediaz.carbonbox.documentprocessing.application.commands.CreateUploadCommandHandler;
import io.github.ignacioediaz.carbonbox.documentprocessing.application.commands.RedriveUploadCommand;
import io.github.ignacioediaz.carbonbox.documentprocessing.application.commands.RedriveUploadCommandHandler;
import io.github.ignacioediaz.carbonbox.documentprocessing.application.ports.FileStorage;
import io.github.ignacioediaz.carbonbox.documentprocessing.domain.UploadRepository;
import io.github.ignacioediaz.carbonbox.documentprocessing.domain.UploadStatus;
import io.github.ignacioediaz.carbonbox.documentprocessing.infrastructure.messaging.DatabaseMessageQueue;
import io.github.ignacioediaz.carbonbox.documentprocessing.infrastructure.messaging.QueueWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reintentos y DLQ con la cola real sobre H2. El almacenamiento falla a proposito; backoff en 0
 * para que el mensaje vuelva a estar visible enseguida.
 */
@SpringBootTest(properties = {
        "carbonbox.worker.enabled=false",
        "carbonbox.queue.max-receive-count=3",
        "carbonbox.queue.backoff-base=PT0S",
        "carbonbox.queue.backoff-max=PT0S",
        "spring.datasource.url=jdbc:h2:mem:cola;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"})
@DirtiesContext
class QueueRetryIntegrationTest {

    /** Guarda en memoria y puede fallar al leer. */
    static class FlakyStorage implements FileStorage {
        final Map<String, byte[]> files = new HashMap<>();
        volatile boolean failing;

        @Override
        public void save(String key, byte[] content) {
            files.put(key, content);
        }

        @Override
        public byte[] read(String key) throws IOException {
            if (failing) {
                throw new IOException("almacenamiento caído");
            }
            return files.get(key);
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        FlakyStorage flakyStorage() {
            return new FlakyStorage();
        }
    }

    @Autowired
    CreateUploadCommandHandler create;
    @Autowired
    RedriveUploadCommandHandler redrive;
    @Autowired
    QueueWorker worker;
    @Autowired
    DatabaseMessageQueue queue;
    @Autowired
    UploadRepository uploads;
    @Autowired
    FlakyStorage storage;

    private UUID subir(String marca) {
        String csv = "category,amount,unit,date\nelectricity,10,kWh,2026-01-01\n# " + marca + ",1,kg,2026-01-01\n";
        return create.handle(new CreateUploadCommand("a.csv", csv.getBytes(StandardCharsets.UTF_8), null))
                .getRight().uploadId();
    }

    @Test
    void reintentaYAlAgotarLosIntentosVaALaDlqYElUploadFalla() {
        storage.failing = true;
        UUID id = subir("dlq");

        worker.pollOnce();
        assertThat(uploads.findById(id).orElseThrow().status()).isEqualTo(UploadStatus.PENDING); // vuelve a la cola
        worker.pollOnce();
        worker.pollOnce(); // tercer intento: maxReceiveCount

        var upload = uploads.findById(id).orElseThrow();
        assertThat(upload.status()).isEqualTo(UploadStatus.FAILED);
        assertThat(upload.failureReason()).contains("almacenamiento caído");
        assertThat(queue.deadLetters()).anySatisfy(d -> {
            assertThat(d.uploadId()).isEqualTo(id);
            assertThat(d.receiveCount()).isEqualTo(3);
        });
        assertThat(worker.pollOnce()).isZero(); // nada mas en la cola

        // se arregla el almacenamiento y se reenvia desde la DLQ
        storage.failing = false;
        assertThat(redrive.handle(new RedriveUploadCommand(id)).isRight()).isTrue();
        assertThat(queue.deadLetters()).noneSatisfy(d -> assertThat(d.uploadId()).isEqualTo(id));
        worker.pollOnce();
        assertThat(uploads.findById(id).orElseThrow().status()).isEqualTo(UploadStatus.COMPLETED);
    }

    @Test
    void unMensajeTomadoNoLoVeOtroWorkerHastaQueVenceElVisibilityTimeout() {
        storage.failing = false;
        UUID id = subir("visibilidad");
        var primeros = queue.receive(10, Duration.ofMinutes(5));
        assertThat(primeros).extracting(DatabaseMessageQueue.ReceivedMessage::uploadId).contains(id);
        assertThat(queue.receive(10, Duration.ofMinutes(5)))
                .extracting(DatabaseMessageQueue.ReceivedMessage::uploadId).doesNotContain(id);

        // con timeout cero vuelve a estar visible enseguida: simula un worker que se cayo
        var otro = subir("timeout");
        var tomados = queue.receive(10, Duration.ZERO);
        assertThat(tomados).extracting(DatabaseMessageQueue.ReceivedMessage::uploadId).contains(otro);
        var deNuevo = queue.receive(10, Duration.ofMinutes(5));
        assertThat(deNuevo).filteredOn(m -> m.uploadId().equals(otro))
                .singleElement().satisfies(m -> assertThat(m.receiveCount()).isEqualTo(2));
    }
}
