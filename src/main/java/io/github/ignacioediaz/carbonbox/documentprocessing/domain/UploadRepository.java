package io.github.ignacioediaz.carbonbox.documentprocessing.domain;

import java.util.Optional;
import java.util.UUID;

public interface UploadRepository {

    Optional<Upload> findById(UUID id);

    Optional<Upload> findByIdempotencyKey(IdempotencyKey key);

    void save(Upload upload);
}
