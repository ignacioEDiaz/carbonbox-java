package io.github.ignacioediaz.carbonbox.documentprocessing.domain;

import io.github.ignacioediaz.carbonbox.shared.domain.DomainError;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UploadTest {

    private final Instant now = Instant.parse("2026-11-23T12:00:00Z");

    private Upload nuevo() {
        return Upload.create(UUID.randomUUID(), new FileName("a.csv"), "a.csv", new IdempotencyKey("k"), now);
    }

    @Test
    void empiezaPendienteYTerminaCompletado() {
        Upload u = nuevo();
        assertThat(u.status()).isEqualTo(UploadStatus.PENDING);
        assertThat(u.startProcessing(now).isRight()).isTrue();
        assertThat(u.complete(10, 2, now.plusSeconds(1)).isRight()).isTrue();
        assertThat(u.status()).isEqualTo(UploadStatus.COMPLETED);
        assertThat(u.processedRows()).isEqualTo(10);
        assertThat(u.errorRows()).isEqualTo(2);
    }

    @Test
    void noSePuedeCompletarSinHaberEmpezado() {
        Upload u = nuevo();
        var r = u.complete(1, 0, now);
        assertThat(r.getLeft()).isInstanceOf(DomainError.Conflict.class);
        assertThat(u.status()).isEqualTo(UploadStatus.PENDING);
    }

    @Test
    void unCompletadoNoCambiaMas() {
        Upload u = nuevo();
        u.startProcessing(now);
        u.complete(1, 0, now);
        assertThat(u.fail("x", now).isLeft()).isTrue();
        assertThat(u.backToPending(now).isLeft()).isTrue();
    }

    @Test
    void unFallidoSePuedeReencolarYPierdeElMotivo() {
        Upload u = nuevo();
        u.startProcessing(now);
        u.fail("disco lleno", now);
        assertThat(u.failureReason()).isEqualTo("disco lleno");
        assertThat(u.backToPending(now).isRight()).isTrue();
        assertThat(u.status()).isEqualTo(UploadStatus.PENDING);
        assertThat(u.failureReason()).isNull();
    }

    @Test
    void nombreDeArchivoSoloCsvYSinRuta() {
        assertThat(FileName.of("C:\\datos\\enero.csv").getRight().value()).isEqualTo("enero.csv");
        assertThat(FileName.of("foto.png").isLeft()).isTrue();
        assertThat(FileName.of(" ").isLeft()).isTrue();
    }

    @Test
    void mismaClaveParaElMismoContenido() {
        byte[] a = "category,amount,unit,date\nx,1,kg,2026-01-01".getBytes();
        assertThat(IdempotencyKey.fromContent(a)).isEqualTo(IdempotencyKey.fromContent(a.clone()));
        assertThat(IdempotencyKey.fromHeader("pedido-123").isRight()).isTrue();
        assertThat(IdempotencyKey.fromHeader("con espacios").isLeft()).isTrue();
    }
}
