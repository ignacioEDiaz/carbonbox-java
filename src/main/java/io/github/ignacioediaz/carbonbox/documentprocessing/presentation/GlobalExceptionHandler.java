package io.github.ignacioediaz.carbonbox.documentprocessing.presentation;

import io.github.ignacioediaz.carbonbox.documentprocessing.presentation.ApiResponses.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/** Lo que no es un error de negocio: pedidos mal armados y fallas inesperadas. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MissingServletRequestPartException.class)
    ResponseEntity<ApiError> missingFile(MissingServletRequestPartException e) {
        return body(HttpStatus.BAD_REQUEST, "Falta el archivo (campo \"file\" del formulario)");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> badParameter(MethodArgumentTypeMismatchException e) {
        return body(HttpStatus.BAD_REQUEST, "Parámetro inválido: " + e.getName());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> tooLarge(MaxUploadSizeExceededException e) {
        return body(HttpStatus.PAYLOAD_TOO_LARGE, "El archivo es demasiado grande");
    }

    /** Dos subidas iguales al mismo tiempo: la restriccion UNIQUE de idempotency_key frena la segunda. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> conflict(DataIntegrityViolationException e) {
        return body(HttpStatus.CONFLICT, "Ese archivo ya se está subiendo; consultá la lista de uploads");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception e) {
        log.error("Error inesperado", e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "Error inesperado");
    }

    private static ResponseEntity<ApiError> body(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(status.name(), message));
    }
}
