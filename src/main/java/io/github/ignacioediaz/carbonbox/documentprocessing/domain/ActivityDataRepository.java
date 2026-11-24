package io.github.ignacioediaz.carbonbox.documentprocessing.domain;

import java.util.List;
import java.util.UUID;

public interface ActivityDataRepository {

    /**
     * Reemplaza las filas de un upload. Borrar y volver a insertar hace que reprocesar un mensaje
     * repetido (la cola entrega "al menos una vez") no duplique datos.
     */
    void replaceForUpload(UUID uploadId, List<ActivityData> rows);
}
