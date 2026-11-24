package io.github.ignacioediaz.carbonbox.shared.domain;

import java.util.function.Function;

/**
 * Resultado de una operacion que puede fallar por una regla de negocio.
 * Los errores esperables (validacion, no encontrado, conflicto) viajan como valor en Left;
 * las excepciones quedan para lo inesperado (disco, base de datos caida), que el worker reintenta.
 */
public sealed interface Either<L, R> permits Either.Left, Either.Right {

    record Left<L, R>(L value) implements Either<L, R> {
    }

    record Right<L, R>(R value) implements Either<L, R> {
    }

    static <L, R> Either<L, R> left(L value) {
        return new Left<>(value);
    }

    static <L, R> Either<L, R> right(R value) {
        return new Right<>(value);
    }

    default boolean isRight() {
        return this instanceof Right;
    }

    default boolean isLeft() {
        return this instanceof Left;
    }

    default R getRight() {
        if (this instanceof Right<L, R> r) {
            return r.value();
        }
        throw new IllegalStateException("Es un Left: " + ((Left<L, R>) this).value());
    }

    default L getLeft() {
        if (this instanceof Left<L, R> l) {
            return l.value();
        }
        throw new IllegalStateException("Es un Right");
    }

    default <T> Either<L, T> map(Function<? super R, ? extends T> f) {
        return switch (this) {
            case Left<L, R> l -> Either.left(l.value());
            case Right<L, R> r -> Either.right(f.apply(r.value()));
        };
    }

    default <T> Either<L, T> flatMap(Function<? super R, Either<L, T>> f) {
        return switch (this) {
            case Left<L, R> l -> Either.left(l.value());
            case Right<L, R> r -> f.apply(r.value());
        };
    }

    default <T> T fold(Function<? super L, ? extends T> onLeft, Function<? super R, ? extends T> onRight) {
        return switch (this) {
            case Left<L, R> l -> onLeft.apply(l.value());
            case Right<L, R> r -> onRight.apply(r.value());
        };
    }
}
