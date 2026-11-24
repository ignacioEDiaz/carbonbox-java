package io.github.ignacioediaz.carbonbox.documentprocessing.application.queries;

import io.github.ignacioediaz.carbonbox.documentprocessing.application.queries.ReadModels.ActivityView;
import io.github.ignacioediaz.carbonbox.documentprocessing.application.queries.ReadModels.MonthlyActivityView;
import io.github.ignacioediaz.carbonbox.documentprocessing.application.queries.ReadModels.PageView;
import io.github.ignacioediaz.carbonbox.documentprocessing.application.queries.ReadModels.RowErrorView;
import io.github.ignacioediaz.carbonbox.documentprocessing.application.queries.ReadModels.SummaryView;
import io.github.ignacioediaz.carbonbox.documentprocessing.application.queries.ReadModels.UploadReadModel;
import io.github.ignacioediaz.carbonbox.documentprocessing.application.queries.ReadModels.UploadView;
import io.github.ignacioediaz.carbonbox.shared.domain.DomainError;
import io.github.ignacioediaz.carbonbox.shared.domain.Either;

import java.util.List;
import java.util.UUID;

/**
 * Consultas (lado Q de CQRS). Cada metodo atiende un objeto de consulta; ninguno modifica estado.
 * Validan paginacion y existencia del upload, y delegan la lectura al read model.
 */
public class UploadQueryHandlers {

    public record GetUploadByIdQuery(UUID uploadId) {
    }

    public record ListUploadsQuery(int page, int size) {
    }

    public record GetUploadResultsQuery(UUID uploadId, int page, int size) {
    }

    public record GetUploadRowErrorsQuery(UUID uploadId) {
    }

    public static final int MAX_PAGE_SIZE = 100;

    private final UploadReadModel readModel;

    public UploadQueryHandlers(UploadReadModel readModel) {
        this.readModel = readModel;
    }

    public Either<DomainError, UploadView> handle(GetUploadByIdQuery query) {
        return readModel.findUpload(query.uploadId())
                .<Either<DomainError, UploadView>>map(Either::right)
                .orElseGet(() -> Either.left(notFound(query.uploadId())));
    }

    public Either<DomainError, PageView<UploadView>> handle(ListUploadsQuery query) {
        return validPage(query.page(), query.size()).map(ok -> readModel.listUploads(query.page(), query.size()));
    }

    public Either<DomainError, PageView<ActivityView>> handle(GetUploadResultsQuery query) {
        return validPage(query.page(), query.size())
                .flatMap(ok -> exists(query.uploadId()))
                .map(ok -> readModel.listActivity(query.uploadId(), query.page(), query.size()));
    }

    public Either<DomainError, List<RowErrorView>> handle(GetUploadRowErrorsQuery query) {
        return exists(query.uploadId()).map(ok -> readModel.listRowErrors(query.uploadId()));
    }

    public SummaryView summary() {
        return readModel.summary();
    }

    public List<MonthlyActivityView> activityByMonth() {
        return readModel.activityByMonth();
    }

    private Either<DomainError, Boolean> exists(UUID id) {
        return readModel.findUpload(id).isPresent() ? Either.right(true) : Either.left(notFound(id));
    }

    private static Either<DomainError, Boolean> validPage(int page, int size) {
        if (page < 1) {
            return Either.left(new DomainError.Validation("page empieza en 1"));
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            return Either.left(new DomainError.Validation("size debe estar entre 1 y " + MAX_PAGE_SIZE));
        }
        return Either.right(true);
    }

    private static DomainError notFound(UUID id) {
        return new DomainError.NotFound("No existe el upload " + id);
    }
}
