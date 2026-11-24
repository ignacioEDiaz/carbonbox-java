package io.github.ignacioediaz.carbonbox.documentprocessing.application.ports;

import java.io.IOException;

/** Donde quedan los archivos subidos (disco local; un adaptador de S3 implementaria lo mismo). */
public interface FileStorage {

    void save(String key, byte[] content) throws IOException;

    byte[] read(String key) throws IOException;
}
