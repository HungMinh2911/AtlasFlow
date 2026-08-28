package com.atlasflow.file.infrastructure;

import com.atlasflow.file.application.port.FileRepository;
import com.atlasflow.file.domain.AtlasFile;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryFileRepository implements FileRepository {

    private final Map<UUID, AtlasFile> fileMap =
            new ConcurrentHashMap<>();


    @Override
    public AtlasFile save(AtlasFile atlasFilefile){
        fileMap.put(atlasFilefile.id(),atlasFilefile);
        return atlasFilefile;
    }

    @Override
    public Optional<AtlasFile> findById (UUID id){

        return fileMap.values().stream()
                .filter(fileMap -> fileMap.id().equals(id))
                .findFirst();
    }


}
