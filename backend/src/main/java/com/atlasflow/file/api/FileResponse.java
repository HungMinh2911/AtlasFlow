package com.atlasflow.file.api;

import com.atlasflow.file.domain.AtlasFile;

import java.util.UUID;

public record FileResponse(
        UUID id,
        UUID project_id,
        String file_name,
        long size_bytes,
        String checksum,
        String status
) {
    public static FileResponse from(AtlasFile atlasFile){
        return new FileResponse(
                atlasFile.id(),
                atlasFile.projectId(),
                atlasFile.fileName(),
                atlasFile.sizeBytes(),
                atlasFile.checksum(),
                atlasFile.status().name()
        );
    }

}
