package com.atlasflow.file.application;

import com.atlasflow.file.api.FileResponse;
import com.atlasflow.file.application.port.FileRepository;
import com.atlasflow.file.application.port.FileStoragePort;
import com.atlasflow.file.domain.AtlasFile;
import com.atlasflow.file.domain.FileNotFoundException;
import com.atlasflow.file.domain.FileUploadException;
import com.atlasflow.file.domain.InvalidFileException;
import com.atlasflow.project.application.port.ProjectRepository;
import com.atlasflow.project.domain.ProjectNotExistsException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class FileApplicationService {

    private final ProjectRepository projectRepository ;
    private final FileRepository fileRepository;
    private  final FileStoragePort fileStoragePort;

    public FileApplicationService(ProjectRepository projectRepository, FileRepository fileRepository, FileStoragePort fileStoragePort) {
        this.projectRepository = projectRepository;
        this.fileRepository = fileRepository;
        this.fileStoragePort = fileStoragePort;
    }

    public FileResponse uploadFile(UUID projectId, MultipartFile file) {
        String fileName = getSafeFilename(file);
        UUID fileId = UUID.randomUUID();
        String objectKey = projectId + "/" + fileId + "-" + fileName;
        FileResponse fileResponse = createFile(
                fileId,
                projectId,
                fileName,
                objectKey
        );

        try {
            validateFile(file);
            String checkSum = calculateChecksum(file);
            long sizeBytes = file.getSize();

            fileStoragePort.save(objectKey, file.getInputStream());

            return updateFileAvailable(
                    fileResponse.id(),
                    checkSum,
                    sizeBytes
            );

        } catch (InvalidFileException e) {
            updateFileFailed(fileResponse.id());
            throw e;
        } catch (Exception e) {
            updateFileFailed(fileResponse.id());
            throw new FileUploadException("File upload failed", e);
        }
    }

    public FileResponse createFile(
            UUID fileId,
            UUID projectId,
            String fileName,
            String objectKey
    ) {
        projectRepository.findProjectById(projectId)
                .orElseThrow(() -> new ProjectNotExistsException(projectId));

        AtlasFile file = AtlasFile.createUploading(
                fileId,
                projectId,
                fileName,
                objectKey
        );

        return FileResponse.from(fileRepository.save(file));
    }

    public FileResponse updateFileAvailable (
            UUID fileId,
            String checksum,
            long sizeBytes
    ){
        AtlasFile file = fileRepository.findById(fileId)
                .orElseThrow(() -> new FileNotFoundException(fileId));

        AtlasFile a_file = file.markAvailable(checksum,sizeBytes);

        return FileResponse.from(
                fileRepository.save(a_file)
        );
    }

    public FileResponse updateFileFailed (
            UUID fileId

    ){
        AtlasFile file = fileRepository.findById(fileId)
                .orElseThrow(() -> new FileNotFoundException(fileId));


        return FileResponse.from(
                fileRepository.save(file.markFailed())
        );
    }

    private String calculateChecksum(
            MultipartFile file
    ) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        try (InputStream inputStream = file.getInputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }

        return HexFormat.of().formatHex(digest.digest());
    }

    private String getSafeFilename(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();

        if (originalFilename == null || originalFilename.isBlank()) {
            throw new InvalidFileException("File name must not be empty");
        }

        return java.nio.file.Paths.get(originalFilename)
                .getFileName()
                .toString();
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new InvalidFileException("File must not be empty");
        }

        if (file.getSize() < 50 * 1024L) {
            throw new InvalidFileException(
                    "File must be at least 50 KB"
            );
        }

        String filename = getSafeFilename(file).toLowerCase();
        if (!filename.endsWith(".pdf")
                && !filename.endsWith(".docx")
                && !filename.endsWith(".txt")) {
            throw new InvalidFileException(
                    "Only PDF, DOCX and TXT files are allowed"
            );
        }
    }


}
