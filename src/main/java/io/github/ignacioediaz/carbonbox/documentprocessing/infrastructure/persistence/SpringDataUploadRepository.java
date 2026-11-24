package io.github.ignacioediaz.carbonbox.documentprocessing.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SpringDataUploadRepository extends JpaRepository<UploadEntity, UUID> {

    Optional<UploadEntity> findByIdempotencyKey(String idempotencyKey);
}
