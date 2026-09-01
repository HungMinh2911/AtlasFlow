package com.atlasflow.job.api;

import com.atlasflow.job.application.JobApplicationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.HttpStatus;

import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {

    private final JobApplicationService jobApplicationService;

    public JobController(JobApplicationService jobApplicationService) {
        this.jobApplicationService = jobApplicationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public JobResponse createJob(
            @Valid @RequestBody CreateJobRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {
        return jobApplicationService.createJob(request, idempotencyKey);
    }

    @GetMapping("/{jobId}")
    public JobResponse getJob(@PathVariable UUID jobId) {
        return jobApplicationService.getJob(jobId);
    }

    /**
     * Local phase-2 simulation of putting a submitted Job into the queue.
     * RabbitMQ will replace this transition in a later phase.
     */
    @PostMapping("/{jobId}/queue")
    public JobResponse queueJob(@PathVariable UUID jobId) {
        return jobApplicationService.queueJob(jobId);
    }
}
