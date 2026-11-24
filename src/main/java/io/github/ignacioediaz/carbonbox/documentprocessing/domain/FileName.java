package io.github.ignacioediaz.carbonbox.documentprocessing.domain;

import io.github.ignacioediaz.carbonbox.shared.domain.DomainError;
import io.github.ignacioediaz.carbonbox.shared.domain.Either;

/** Nombre del archivo subido: solo .csv, sin rutas. */
public record FileName(String value) {

    private static final int MAX_LENGTH = 255;

    public static Either<DomainError, FileName> of(String raw) {
        if (raw == null || raw.isBlank()) {
            return Either.left(new DomainError.Validation("El archivo no tiene nombre"));
        }
        // el navegador puede mandar la ruta completa: nos quedamos con el nombre
        String name = raw.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).trim();
        if (!name.toLowerCase().endsWith(".csv")) {
            return Either.left(new DomainError.Validation("Solo se aceptan archivos .csv y llegó \"" + name + "\""));
        }
        if (name.length() > MAX_LENGTH) {
            return Either.left(new DomainError.Validation("El nombre del archivo supera los " + MAX_LENGTH + " caracteres"));
        }
        return Either.right(new FileName(name));
    }
}
