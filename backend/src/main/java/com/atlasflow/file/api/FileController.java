package com.atlasflow.file.api;

import com.atlasflow.file.application.FileApplicationService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class FileController {

    private final FileApplicationService fileApplicationService;

    public FileController(FileApplicationService fileApplicationService) {
        this.fileApplicationService = fileApplicationService;
    }

    @PostMapping(
            value = "/projects/{projectId}/files",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public FileResponse uploadFile(
            @PathVariable UUID projectId,
            @RequestPart("file") MultipartFile file
    ) {
        return fileApplicationService.uploadFile(projectId, file);
    }
}
