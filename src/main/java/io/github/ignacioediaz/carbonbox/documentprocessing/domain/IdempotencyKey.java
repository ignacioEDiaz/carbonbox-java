package io.github.ignacioediaz.carbonbox.documentprocessing.domain;

import io.github.ignacioediaz.carbonbox.shared.domain.DomainError;
import io.github.ignacioediaz.carbonbox.shared.domain.Either;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Identifica un pedido de subida para no crear dos uploads iguales si el cliente reintenta.
 * Viene del header Idempotency-Key; si no viene, es el hash SHA-256 del contenido.
 */
public record IdempotencyKey(String value) {

    private static final int MAX_LENGTH = 128;

    public static Either<DomainError, IdempotencyKey> fromHeader(String header) {
        String v = header == null ? "" : header.trim();
        if (v.isEmpty() || v.length() > MAX_LENGTH || !v.matches("[A-Za-z0-9._:-]+")) {
            return Either.left(new DomainError.Validation(
                    "Idempotency-Key debe tener entre 1 y " + MAX_LENGTH + " caracteres (letras, números, . _ : -)"));
        }
        return Either.right(new IdempotencyKey("header:" + v));
    }

    public static IdempotencyKey fromContent(byte[] content) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(content);
            return new IdempotencyKey("sha256:" + HexFormat.of().formatHex(hash));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("La JVM no tiene SHA-256", e);
        }
    }
}
