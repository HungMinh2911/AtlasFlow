package com.atlasflow.file.application.port;

import com.atlasflow.file.domain.AtlasFile;

import java.io.File;
import java.util.Optional;
import java.util.UUID;

public interface FileRepository {

    AtlasFile save(AtlasFile file);

    Optional<AtlasFile> findById (UUID id);
}
