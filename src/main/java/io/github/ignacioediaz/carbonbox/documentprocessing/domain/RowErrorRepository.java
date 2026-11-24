package io.github.ignacioediaz.carbonbox.documentprocessing.domain;

import java.util.List;
import java.util.UUID;

public interface RowErrorRepository {

    void replaceErrorsForUpload(UUID uploadId, List<RowError> errors);
}
