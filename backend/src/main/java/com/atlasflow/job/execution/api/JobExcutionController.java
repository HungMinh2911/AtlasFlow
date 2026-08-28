package com.atlasflow.job.execution.api;

import com.atlasflow.job.execution.application.JobExecutionApplicationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Endpoint local để mô phỏng worker tạo một JobExecution.
 * Sau này có thể thay bằng RabbitMQ consumer ở Phase 6-7.
 */
@RestController
@RequestMapping("/api/v1/job-executions")
public class JobExcutionController {

    private final JobExecutionApplicationService service;

    public JobExcutionController(JobExecutionApplicationService service) {
        this.service = service;
    }

    @PostMapping
    public JobExcutionRespone createJobExecution(
            @Valid @RequestBody CreateJobExcutionRequest request
    ) {
        return service.createJobExecution(request);
    }

    @PatchMapping("/{executionId}/finish")
    public JobExcutionRespone finishExecution(
            @PathVariable UUID executionId,
            @Valid @RequestBody FinishJobExecutionRequest request
    ) {
        return service.finishExecution(executionId, request.status());
    }
}
