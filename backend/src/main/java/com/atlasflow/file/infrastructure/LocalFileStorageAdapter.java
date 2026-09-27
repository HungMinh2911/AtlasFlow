package com.atlasflow.file.infrastructure;

import com.atlasflow.file.application.port.FileStoragePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class LocalFileStorageAdapter implements FileStoragePort {

    private final Path storageRoot;

    public LocalFileStorageAdapter(
            @Value("${atlasflow.storage.local-root}") String storageRoot
    ) {
        this.storageRoot = Path.of(storageRoot)
                .toAbsolutePath()
                .normalize();
    }

    @Override
    public void save(
            String objectKey,
            InputStream inputStream
    ) throws IOException {
        Path targetPath = storageRoot
                .resolve(objectKey)
                .normalize();

        if (!targetPath.startsWith(storageRoot)) {
            throw new IOException("Invalid object key");
        }

        Files.createDirectories(targetPath.getParent());

        Files.copy(
                inputStream,
                targetPath
        );
    }
}
