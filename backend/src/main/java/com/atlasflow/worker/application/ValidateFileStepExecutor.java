package com.atlasflow.worker.application;

import com.atlasflow.file.application.port.FileRepository;
import com.atlasflow.file.domain.AtlasFile;
import com.atlasflow.file.domain.FileNotFoundException;
import com.atlasflow.file.domain.FileStatus;
import com.atlasflow.file.domain.InvalidFileException;
import com.atlasflow.job.domain.Job;
import com.atlasflow.workflow.step.domain.InvalidWorkflowStepConfigException;
import com.atlasflow.workflow.step.domain.WorkflowStep;
import com.atlasflow.workflow.step.domain.WorkflowStepType;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Locale;
@Component
public class ValidateFileStepExecutor implements StepExecutor {

    private final FileRepository fileRepository;

    public ValidateFileStepExecutor(FileRepository fileRepository) {

        this.fileRepository = fileRepository;
    }

    @Override
    public boolean supports(String stepType) {

        return WorkflowStepType.VALIDATE_FILE.name().equals(stepType);
    }

    @Override
    public String taskType() {
        return WorkflowStepType.VALIDATE_FILE.name();
    }

    @Override
    public boolean execute(WorkflowStep step, Job job) {
        // Implementation for validating a file

        AtlasFile file = fileRepository.findById(job.fileId())
                .orElseThrow(() -> new FileNotFoundException(job.fileId()));

        if (file.status() != FileStatus.AVAILABLE){
            throw new InvalidFileException(
                    "File is not available: " + file.id()
            );

        }

        JsonNode config = step.configJson();

        if (config == null || !config.isObject()){
            throw new InvalidWorkflowStepConfigException(
                    "Validate file config must be a JSON object"
            );

        }

        JsonNode allowExtensions = config.get("allowed_extensions");

        if (allowExtensions == null || !allowExtensions.isArray()
              || allowExtensions.isEmpty()){

            throw new InvalidWorkflowStepConfigException(
                    "allowed_extensions must be a non-empty array"
            );

        }

        String extension = extractExtension(file.fileName());
        boolean check = false;
        for (JsonNode item : allowExtensions){
            String configExtension = item.asText().
                    toLowerCase(Locale.ROOT).replace(".","");

            if (configExtension.equals(extension)) {
                check = true;
                break;
            }
        }

        if (!check) {
            return false;
        }

        return true;
    }

    private String extractExtension (String fileName){

        if (fileName == null || fileName.isBlank()) {
            throw new InvalidFileException("File name is null or blank");
        }

        int lastDotIndex = fileName.lastIndexOf('.');

        if (lastDotIndex <= 0 || lastDotIndex == fileName.length() - 1) {
            throw new InvalidFileException("File name does not have a valid extension: " + fileName);
        }

        return fileName
                .substring(lastDotIndex+1)
                .toLowerCase(Locale.ROOT);
    }
}
