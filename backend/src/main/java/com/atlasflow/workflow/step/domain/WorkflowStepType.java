package com.atlasflow.workflow.step.domain;

public enum WorkflowStepType {
    VALIDATE_FILE,
    EXTRACT_TEXT,
    SAVE_RESULT,
    OCR_FILE,
    TRANSFORM_DATA,
    HTTP_REQUEST,
    WEBHOOK
}
