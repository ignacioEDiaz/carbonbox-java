package io.github.ignacioediaz.carbonbox.documentprocessing.application.ports;

import java.util.function.Supplier;

/** Ejecuta un bloque en una transaccion, sin que la capa de aplicacion dependa de Spring. */
public interface UnitOfWork {

    <T> T inTransaction(Supplier<T> work);
}
