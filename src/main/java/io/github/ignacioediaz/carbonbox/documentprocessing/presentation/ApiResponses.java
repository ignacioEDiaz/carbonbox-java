package io.github.ignacioediaz.carbonbox.documentprocessing.presentation;

import io.github.ignacioediaz.carbonbox.shared.domain.DomainError;
import io.github.ignacioediaz.carbonbox.shared.domain.Either;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Traduce el Either de los casos de uso a una respuesta HTTP. */
public final class ApiResponses {

    public record ApiError(String error, String message) {
    }

    private ApiResponses() {
    }

    public static <T> ResponseEntity<?> ok(Either<DomainError, T> result) {
        return result.fold(ApiResponses::error, ResponseEntity::ok);
    }

    public static ResponseEntity<ApiError> error(DomainError e) {
        HttpStatus status = switch (e) {
            case DomainError.Validation v -> HttpStatus.BAD_REQUEST;
            case DomainError.NotFound n -> HttpStatus.NOT_FOUND;
            case DomainError.Conflict c -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status).body(new ApiError(status.name(), e.message()));
    }
}
