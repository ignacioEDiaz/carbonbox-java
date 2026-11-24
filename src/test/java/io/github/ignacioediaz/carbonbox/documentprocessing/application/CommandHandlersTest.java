package io.github.ignacioediaz.carbonbox.documentprocessing.application;

import io.github.ignacioediaz.carbonbox.documentprocessing.Fakes;
import io.github.ignacioediaz.carbonbox.documentprocessing.application.commands.CreateUploadCommand;
import io.github.ignacioediaz.carbonbox.documentprocessing.application.commands.CreateUploadCommandHandler;
import io.github.ignacioediaz.carbonbox.documentprocessing.application.commands.ProcessFileCommand;
import io.github.ignacioediaz.carbonbox.documentprocessing.application.commands.ProcessFileCommandHandler;
import io.github.ignacioediaz.carbonbox.documentprocessing.application.services.CsvParser;
import io.github.ignacioediaz.carbonbox.documentprocessing.domain.UploadStatus;
import io.github.ignacioediaz.carbonbox.shared.domain.DomainError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommandHandlersTest {

    private static final String CSV = "category,amount,unit,date\nelectricity,10,kWh,2026-01-01\nmalo,-1,kg,2026-01-02\n";

    private Fakes.Uploads uploads;
    private Fakes.Rows rows;
    private Fakes.Storage storage;
    private Fakes.Publisher publisher;
    private CreateUploadCommandHandler create;
    private ProcessFileCommandHandler process;

    @BeforeEach
    void setUp() {
        uploads = new Fakes.Uploads();
        rows = new Fakes.Rows();
        storage = new Fakes.Storage();
        publisher = new Fakes.Publisher();
        Clock clock = Clock.fixed(Instant.parse("2026-11-23T12:00:00Z"), ZoneOffset.UTC);
        var uow = new Fakes.DirectUnitOfWork();
        create = new CreateUploadCommandHandler(uploads, storage, publisher, uow, clock, 1024 * 1024);
        process = new ProcessFileCommandHandler(uploads, rows, rows, storage, uow, new CsvParser(), clock);
    }

    private UUID subir(String csv) {
        return create.handle(new CreateUploadCommand("datos.csv", csv.getBytes(StandardCharsets.UTF_8), null))
                .getRight().uploadId();
    }

    @Test
    void subirGuardaElArchivoCreaPendienteYEncola() {
        UUID id = subir(CSV);
        assertThat(uploads.byId.get(id).status()).isEqualTo(UploadStatus.PENDING);
        assertThat(storage.files).containsKey(id + ".csv");
        assertThat(publisher.published).containsExactly(id);
    }

    @Test
    void elMismoArchivoDosVecesDevuelveElMismoUpload() {
        UUID primero = subir(CSV);
        var segundo = create.handle(new CreateUploadCommand("otro-nombre.csv", CSV.getBytes(StandardCharsets.UTF_8), null))
                .getRight();
        assertThat(segundo.uploadId()).isEqualTo(primero);
        assertThat(segundo.created()).isFalse();
        assertThat(publisher.published).hasSize(1);
        assertThat(storage.files).hasSize(1);
    }

    @Test
    void unArchivoInvalidoNoSeGuardaNiSeEncola() {
        var r = create.handle(new CreateUploadCommand("x.csv", "nombre,edad\nana,3".getBytes(StandardCharsets.UTF_8), null));
        assertThat(r.getLeft()).isInstanceOf(DomainError.Validation.class);
        var vacio = create.handle(new CreateUploadCommand("x.csv", new byte[0], null));
        assertThat(vacio.isLeft()).isTrue();
        assertThat(storage.files).isEmpty();
        assertThat(publisher.published).isEmpty();
    }

    @Test
    void procesarGuardaFilasYErroresYCompleta() {
        UUID id = subir(CSV);
        var r = process.handle(new ProcessFileCommand(id));
        assertThat(r.getRight()).isEqualTo(UploadStatus.COMPLETED);
        assertThat(rows.activity.get(id)).hasSize(1);
        assertThat(rows.errors.get(id)).hasSize(1);
        assertThat(uploads.byId.get(id).processedRows()).isEqualTo(1);
        assertThat(uploads.byId.get(id).errorRows()).isEqualTo(1);
    }

    @Test
    void unMensajeRepetidoNoReprocesa() {
        UUID id = subir(CSV);
        process.handle(new ProcessFileCommand(id));
        rows.activity.clear();
        var r = process.handle(new ProcessFileCommand(id));
        assertThat(r.getRight()).isEqualTo(UploadStatus.COMPLETED);
        assertThat(rows.activity).isEmpty(); // no volvio a escribir
    }

    @Test
    void sinFilasValidasQuedaFallidoConElMotivo() {
        UUID id = subir("category,amount,unit,date\nx,-1,kg,2026-01-01\n");
        assertThat(process.handle(new ProcessFileCommand(id)).getRight()).isEqualTo(UploadStatus.FAILED);
        assertThat(uploads.byId.get(id).failureReason()).contains("Ninguna fila es válida");
    }

    @Test
    void unErrorDeDiscoSePropagaParaQueElWorkerReintente() {
        UUID id = subir(CSV);
        storage.failReads = true;
        assertThatThrownBy(() -> process.handle(new ProcessFileCommand(id))).isInstanceOf(UncheckedIOException.class);
        assertThat(uploads.byId.get(id).status()).isEqualTo(UploadStatus.PROCESSING);
    }

    @Test
    void procesarUnUploadQueNoExisteEsUnErrorDeNegocio() {
        assertThat(process.handle(new ProcessFileCommand(UUID.randomUUID())).getLeft())
                .isInstanceOf(DomainError.NotFound.class);
    }
}
