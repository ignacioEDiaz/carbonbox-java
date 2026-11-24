package io.github.ignacioediaz.carbonbox.documentprocessing.infrastructure.persistence;

import io.github.ignacioediaz.carbonbox.documentprocessing.domain.FileName;
import io.github.ignacioediaz.carbonbox.documentprocessing.domain.IdempotencyKey;
import io.github.ignacioediaz.carbonbox.documentprocessing.domain.Upload;
import io.github.ignacioediaz.carbonbox.documentprocessing.domain.UploadRepository;
import io.github.ignacioediaz.carbonbox.documentprocessing.domain.UploadStatus;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** Adaptador: guarda y reconstruye el agregado Upload con JPA (mapper entre dominio y entidad). */
@Repository
public class JpaUploadRepository implements UploadRepository {

    private final SpringDataUploadRepository jpa;

    public JpaUploadRepository(SpringDataUploadRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Upload> findById(UUID id) {
        return jpa.findById(id).map(JpaUploadRepository::toDomain);
    }

    @Override
    public Optional<Upload> findByIdempotencyKey(IdempotencyKey key) {
        return jpa.findByIdempotencyKey(key.value()).map(JpaUploadRepository::toDomain);
    }

    @Override
    public void save(Upload u) {
        jpa.saveAndFlush(new UploadEntity(u.id(), u.fileName().value(), u.storageKey(), u.idempotencyKey().value(),
                u.status().name(), u.processedRows(), u.errorRows(), u.failureReason(), u.uploadedAt(), u.updatedAt()));
    }

    static Upload toDomain(UploadEntity e) {
        return Upload.restore(e.getId(), new FileName(e.getFileName()), e.getStorageKey(),
                new IdempotencyKey(e.getIdempotencyKey()), e.getUploadedAt(), UploadStatus.valueOf(e.getStatus()),
                e.getProcessedRows(), e.getErrorRows(), e.getFailureReason(), e.getUpdatedAt());
    }
}
