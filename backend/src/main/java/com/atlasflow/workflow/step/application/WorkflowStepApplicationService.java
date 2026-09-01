package com.atlasflow.workflow.step.application;

import com.atlasflow.workflow.application.port.WorkflowRepository;
import com.atlasflow.workflow.domain.WorkflowNotFoundException;
import com.atlasflow.workflow.step.api.CreateWorkflowStepRequest;
import com.atlasflow.workflow.step.api.WorkflowStepResponse;
import com.atlasflow.workflow.step.application.port.WorkflowStepRepository;
import com.atlasflow.workflow.step.domain.InvalidWorkflowStepConfigException;
import com.atlasflow.workflow.step.domain.WorkflowStep;
import com.atlasflow.workflow.step.domain.WorkflowStepType;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.UUID;

@Service
public class WorkflowStepApplicationService {

    private final WorkflowRepository workflowRepository;
    private final WorkflowStepRepository workflowStepRepository;

    public WorkflowStepApplicationService(
            WorkflowRepository workflowRepository,
            WorkflowStepRepository workflowStepRepository
    ) {
        this.workflowRepository = workflowRepository;
        this.workflowStepRepository = workflowStepRepository;
    }

    public WorkflowStepResponse createWorkflowStep(
            UUID workflowId,
            CreateWorkflowStepRequest request
    ) {
        workflowRepository.findById(workflowId)
                .orElseThrow(() -> new WorkflowNotFoundException(workflowId));

        if (workflowStepRepository.existsByWorkflowIdAndPosition(
                workflowId,
                request.position()
        )) {
            throw new InvalidWorkflowStepConfigException(
                    "position already exists in this workflow"
            );
        }

        validateConfig(request.task_type(), request.config_json());

        WorkflowStep step = WorkflowStep.create(
                UUID.randomUUID(),
                workflowId,
                request.position(),
                request.task_type(),
                request.config_json()
        );

        return WorkflowStepResponse.from(
                workflowStepRepository.save(step)
        );
    }

    private void validateConfig(
            WorkflowStepType taskType,
            JsonNode config
    ) {
        if (config == null || !config.isObject()) {
            throw new InvalidWorkflowStepConfigException(
                    "config_json must be a JSON object"
            );
        }

        switch (taskType) {
            case VALIDATE_FILE ->
                    validateValidateFileConfig(config);

            case EXTRACT_TEXT ->
                    validateExtractTextConfig(config);

            case SAVE_RESULT ->
                    validateSaveResultConfig(config);

            case OCR_FILE ->
                    validateOcrFileConfig(config);

            case HTTP_REQUEST ->
                    validateHttpRequestConfig(config);

            case WEBHOOK ->
                    validateWebhookConfig(config);

            case TRANSFORM_DATA ->
                    validateTransformDataConfig(config);
        }
    }

    private void validateValidateFileConfig(JsonNode config) {
        JsonNode extensions = requireArray(config, "allowed_extensions");
        if (extensions.isEmpty()) {
            throw invalid("allowed_extensions must not be empty");
        }

        int minSizeKb = requireNonNegativeInt(config, "min_size_kb");
        JsonNode maxSizeNode = config.get("max_size_kb");

        if (maxSizeNode != null) {
            int maxSizeKb = requireNonNegativeInt(config, "max_size_kb");
            if (maxSizeKb < minSizeKb) {
                throw invalid("max_size_kb must be greater than min_size_kb");
            }
        }
    }

    private void validateExtractTextConfig(JsonNode config) {
        requireText(config, "language");

        JsonNode encoding = config.get("encoding");
        if (encoding != null && encoding.isNull()) {
            throw invalid("encoding must not be null");
        }
    }

    private void validateSaveResultConfig(JsonNode config) {
        String destination = requireText(config, "destination").toUpperCase();
        if (!destination.equals("LOCAL") && !destination.equals("S3")) {
            throw invalid("destination must be LOCAL or S3");
        }

        requireText(config, "path");
    }


//    OCR một file — đọc nội dung trong file bằng công nghệ OCR.
    private void validateOcrFileConfig(JsonNode config) {
        JsonNode languages = requireArray(config, "language");
        if (languages.isEmpty()) {
            throw invalid("language must not be empty");
        }

        requireText(config, "engine");
    }

    private void validateTransformDataConfig(JsonNode config) {
        requireText(config, "input_format");
        requireText(config, "output_format");

        JsonNode rules = requireArray(config, "rules");
        if (rules.isEmpty()) {
            throw invalid("rules must not be empty");
        }
    }

    private void validateHttpRequestConfig(JsonNode config) {
        String method = requireText(config, "method").toUpperCase();
        if (!method.equals("GET")
                && !method.equals("POST")
                && !method.equals("PUT")
                && !method.equals("PATCH")
                && !method.equals("DELETE")) {
            throw invalid("method is not supported");
        }

        String url = requireText(config, "url");
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw invalid("url must start with http:// or https://");
        }

        requireNonNegativeInt(config, "timeout_seconds");
        requireNonNegativeInt(config, "retry_count");
    }

    private void validateWebhookConfig(JsonNode config) {
        String url = requireText(config, "url");
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw invalid("url must start with http:// or https://");
        }

        String method = requireText(config, "method").toUpperCase();
        if (!method.equals("POST") && !method.equals("PUT")) {
            throw invalid("WEBHOOK method must be POST or PUT");
        }

        JsonNode events = requireArray(config, "events");
        if (events.isEmpty()) {
            throw invalid("events must not be empty");
        }

        requireNonNegativeInt(config, "retry_count");
    }

    private String requireText(JsonNode config, String fieldName) {
        JsonNode value = config.get(fieldName);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw invalid(fieldName + " is required and must be text");
        }
        return value.asText().trim();
    }

    private JsonNode requireArray(JsonNode config, String fieldName) {
        JsonNode value = config.get(fieldName);
        if (value == null || !value.isArray()) {
            throw invalid(fieldName + " is required and must be an array");
        }
        return value;
    }

    private int requireNonNegativeInt(JsonNode config, String fieldName) {
        JsonNode value = config.get(fieldName);
        if (value == null
                || !value.canConvertToInt()
                || value.asInt() < 0) {
            throw invalid(fieldName + " is required and must be >= 0");
        }
        return value.asInt();
    }

    private InvalidWorkflowStepConfigException invalid(String message) {
        return new InvalidWorkflowStepConfigException(message);
    }



    public List<WorkflowStepResponse> responseList(UUID workflowId){
        workflowRepository.findById(workflowId)
                .orElseThrow(() -> new WorkflowNotFoundException(workflowId));

        return workflowStepRepository.getListWorkflowStepByWorkflowId(workflowId)
                .stream().map(WorkflowStepResponse::from).toList();
    }
}
