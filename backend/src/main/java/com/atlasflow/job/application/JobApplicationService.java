package com.atlasflow.job.application;

import com.atlasflow.file.application.port.FileRepository;
import com.atlasflow.file.domain.AtlasFile;
import com.atlasflow.file.domain.FileNotFoundException;
import com.atlasflow.file.domain.FileStatus;
import com.atlasflow.job.api.CreateJobRequest;
import com.atlasflow.job.api.JobResponse;
import com.atlasflow.job.application.port.JobRepository;
import com.atlasflow.job.domain.Job;
import com.atlasflow.job.domain.JobNotFoundException;
import com.atlasflow.job.domain.JobValidationException;
import com.atlasflow.job.domain.IdempotencyKeyReusedException;
import com.atlasflow.outbox_events.application.OutboxEventsApplicationService;
import com.atlasflow.project.application.port.ProjectRepository;
import com.atlasflow.project.domain.ProjectNotExistsException;
import com.atlasflow.workflow.application.port.WorkflowRepository;
import com.atlasflow.workflow.domain.Workflow;
import com.atlasflow.workflow.domain.WorkflowNotFoundException;
import com.atlasflow.workflow.domain.WorkflowStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class JobApplicationService {

    private final ProjectRepository projectRepository;
    private final WorkflowRepository workflowRepository;
    private final FileRepository fileRepository;
    private final JobRepository jobRepository;
    private final OutboxEventsApplicationService outboxEventsApplicationService;

    public JobApplicationService(
            ProjectRepository projectRepository,
            WorkflowRepository workflowRepository,
            FileRepository fileRepository,
            JobRepository jobRepository,
            OutboxEventsApplicationService outboxEventsApplicationService
    ) {
        this.projectRepository = projectRepository;
        this.workflowRepository = workflowRepository;
        this.fileRepository = fileRepository;
        this.jobRepository = jobRepository;
        this.outboxEventsApplicationService = outboxEventsApplicationService;
    }

    public JobResponse createJob(CreateJobRequest request) {
        return createJob(request, generateLegacyKey(request));
    }

    @Transactional
    public JobResponse createJob(CreateJobRequest request, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new JobValidationException("Idempotency-Key is required");
        }
        if (idempotencyKey.length() > 255) {
            throw new JobValidationException("Idempotency-Key must be at most 255 characters");
        }
        projectRepository.findProjectById(request.project_id())
                .orElseThrow(() -> new ProjectNotExistsException(
                        request.project_id()
                ));

        Workflow workflow = workflowRepository.findById(request.workflow_id())
                .orElseThrow(() -> new WorkflowNotFoundException(
                        request.workflow_id()
                ));

        AtlasFile file = fileRepository.findById(request.file_id())
                .orElseThrow(() -> new FileNotFoundException(
                        request.file_id()
                ));

        if (!workflow.project_id().equals(request.project_id())) {
            throw new JobValidationException(
                    "Workflow does not belong to project"
            );
        }

        if (!file.projectId().equals(request.project_id())) {
            throw new JobValidationException(
                    "File does not belong to project"
            );
        }

        if (workflow.status() != WorkflowStatus.PUBLISHED) {
            throw new JobValidationException(
                    "Workflow must be PUBLISHED before creating a job"
            );
        }

        if (file.status() != FileStatus.AVAILABLE) {
            throw new JobValidationException(
                    "File must be AVAILABLE before creating a job"
            );
        }

        String requestFingerprint = fingerprint(request);

        return jobRepository.findByProjectIdAndIdempotencyKey(request.project_id(), idempotencyKey)
                .map(existing -> {
                    if (existing.requestFingerprint() != null
                            && !existing.requestFingerprint().equals(requestFingerprint)) {
                        throw new IdempotencyKeyReusedException(idempotencyKey);
                    }

                    return JobResponse.from(existing);
                })
                .orElseGet(() -> {
                    Job job = Job.submit(
                            UUID.randomUUID(),
                            request.project_id(),
                            workflow.id(),
                            file.id(),
                            idempotencyKey,
                            requestFingerprint
                    );

                    Optional<Job> inserted = jobRepository.insertIfAbsent(job);
                    if (inserted.isPresent()) {
                        Job savedJob = inserted.get();

                        // 2. Tạo và lưu Outbox Event (cùng transaction với Job)
                        JsonNode payload = outboxEventsApplicationService.createPayload(
                                savedJob.id(),
                                savedJob.projectId(),
                                savedJob.workflowId(),
                                savedJob.fileId()
                        );
                        outboxEventsApplicationService.createOutboxEvents(
                                "JOB",
                                savedJob.id(),
                                "JOB_SUBMITTED",
                                payload
                        );

                        return JobResponse.from(savedJob);
                    } else {
                        // Concurrent request already inserted the job
                        return jobRepository.findByProjectIdAndIdempotencyKey(request.project_id(), idempotencyKey)
                                .map(existing -> {
                                    if (existing.requestFingerprint() != null
                                            && !existing.requestFingerprint().equals(requestFingerprint)) {
                                        throw new IdempotencyKeyReusedException(idempotencyKey);
                                    }
                                    return JobResponse.from(existing);
                                })
                                .orElseThrow(() -> new IllegalStateException("Failed to find concurrently submitted job"));
                    }
                });
    }

    public JobResponse queueJob(UUID id) {

        Job submittedJob = jobRepository.findById(id)
                .orElseThrow(() -> new JobNotFoundException(id));

        Job queuedJob = submittedJob.markQueued();

        return JobResponse.from(jobRepository.save(queuedJob));
    }

    public JobResponse getJob(UUID jobId) {
        return jobRepository.findById(jobId)
                .map(JobResponse::from)
                .orElseThrow(() -> new JobNotFoundException(jobId));
    }


    private String generateLegacyKey(CreateJobRequest request) {
        return generateIdempotencyKey(request.workflow_id(), request.file_id());
    }

    private String generateIdempotencyKey(
            UUID workflowId,
            UUID fileId
    ) {
        String rawKey = workflowId + "|" + fileId;

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(rawKey.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is not available",
                    exception
            );
        }
    }

    private String fingerprint(CreateJobRequest request) {
        String raw = request.project_id() + "|" + request.workflow_id() + "|" + request.file_id();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
