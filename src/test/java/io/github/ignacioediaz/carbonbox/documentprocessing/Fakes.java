package io.github.ignacioediaz.carbonbox.documentprocessing;

import io.github.ignacioediaz.carbonbox.documentprocessing.application.ports.FileStorage;
import io.github.ignacioediaz.carbonbox.documentprocessing.application.ports.MessagePublisher;
import io.github.ignacioediaz.carbonbox.documentprocessing.application.ports.UnitOfWork;
import io.github.ignacioediaz.carbonbox.documentprocessing.domain.ActivityData;
import io.github.ignacioediaz.carbonbox.documentprocessing.domain.ActivityDataRepository;
import io.github.ignacioediaz.carbonbox.documentprocessing.domain.IdempotencyKey;
import io.github.ignacioediaz.carbonbox.documentprocessing.domain.RowError;
import io.github.ignacioediaz.carbonbox.documentprocessing.domain.RowErrorRepository;
import io.github.ignacioediaz.carbonbox.documentprocessing.domain.Upload;
import io.github.ignacioediaz.carbonbox.documentprocessing.domain.UploadRepository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** Dobles en memoria de los puertos, para probar los casos de uso sin base ni Spring. */
public final class Fakes {

    private Fakes() {
    }

    public static class Uploads implements UploadRepository {
        public final Map<UUID, Upload> byId = new HashMap<>();

        @Override
        public Optional<Upload> findById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<Upload> findByIdempotencyKey(IdempotencyKey key) {
            return byId.values().stream().filter(u -> u.idempotencyKey().equals(key)).findFirst();
        }

        @Override
        public void save(Upload upload) {
            byId.put(upload.id(), upload);
        }
    }

    public static class Rows implements ActivityDataRepository, RowErrorRepository {
        public final Map<UUID, List<ActivityData>> activity = new HashMap<>();
        public final Map<UUID, List<RowError>> errors = new HashMap<>();

        @Override
        public void replaceForUpload(UUID uploadId, List<ActivityData> rows) {
            activity.put(uploadId, new ArrayList<>(rows));
        }

        @Override
        public void replaceErrorsForUpload(UUID uploadId, List<RowError> list) {
            errors.put(uploadId, new ArrayList<>(list));
        }
    }

    public static class Storage implements FileStorage {
        public final Map<String, byte[]> files = new HashMap<>();
        public boolean failReads;

        @Override
        public void save(String key, byte[] content) {
            files.put(key, content);
        }

        @Override
        public byte[] read(String key) throws IOException {
            if (failReads) {
                throw new IOException("disco no disponible");
            }
            byte[] b = files.get(key);
            if (b == null) {
                throw new IOException("no existe " + key);
            }
            return b;
        }
    }

    public static class Publisher implements MessagePublisher {
        public final List<UUID> published = new ArrayList<>();

        @Override
        public void publishProcessFile(UUID uploadId) {
            published.add(uploadId);
        }
    }

    public static class DirectUnitOfWork implements UnitOfWork {
        @Override
        public <T> T inTransaction(Supplier<T> work) {
            return work.get();
        }
    }
}
