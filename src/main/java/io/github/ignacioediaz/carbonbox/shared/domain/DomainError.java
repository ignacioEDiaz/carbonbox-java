package io.github.ignacioediaz.carbonbox.shared.domain;

/** Errores de negocio esperables. La capa HTTP los traduce a 400, 404 y 409. */
public sealed interface DomainError permits DomainError.Validation, DomainError.NotFound, DomainError.Conflict {

    String message();

    record Validation(String message) implements DomainError {
    }

    record NotFound(String message) implements DomainError {
    }

    record Conflict(String message) implements DomainError {
    }
}
