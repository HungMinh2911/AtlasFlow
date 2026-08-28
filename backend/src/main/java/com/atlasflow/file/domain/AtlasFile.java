package com.atlasflow.file.domain;

import java.util.UUID;

/**
 * Metadata của một file trong AtlasFlow.
 *
 * Nội dung file được lưu qua FileStoragePort.
 * Đối tượng AtlasFile chỉ mô tả file và status nghiệp vụ của nó.
 */
public record AtlasFile(
        UUID id,
        UUID projectId,
        String fileName,
        String objectKey,
        String checksum,
        long sizeBytes,
        FileStatus status
) {
    public static  AtlasFile createUploading(
            UUID id,
            UUID projectId,
            String fileName,
            String objectKey
    ){
        return new AtlasFile(
                id,
                projectId,
                fileName,
                objectKey,
                null,
                0L,
                FileStatus.UPLOADING

        );
    }
    public AtlasFile markAvailable(
            String checksum,
            long sizeBytes
    ){
        return new AtlasFile(
                id,
                projectId,
                fileName,
                objectKey,
                checksum,
                sizeBytes,
                FileStatus.AVAILABLE
        );
    }
    public AtlasFile markFailed() {
        return new AtlasFile(
                id,
                projectId,
                fileName,
                objectKey,
                checksum,
                sizeBytes,
                FileStatus.FAILED
        );
    }


}
