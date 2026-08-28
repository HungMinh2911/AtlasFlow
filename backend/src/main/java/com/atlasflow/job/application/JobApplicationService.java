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
import com.atlasflow.project.application.port.ProjectRepository;
import com.atlasflow.project.domain.ProjectNotExistsException;
import com.atlasflow.workflow.application.port.WorkflowRepository;
import com.atlasflow.workflow.domain.Workflow;
import com.atlasflow.workflow.domain.WorkflowNotFoundException;
import com.atlasflow.workflow.domain.WorkflowStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class JobApplicationService {

    private final ProjectRepository projectRepository;
    private final WorkflowRepository workflowRepository;
    private final FileRepository fileRepository;
    private final JobRepository jobRepository;

    public JobApplicationService(
            ProjectRepository projectRepository,
            WorkflowRepository workflowRepository,
            FileRepository fileRepository,
            JobRepository jobRepository
    ) {
        this.projectRepository = projectRepository;
        this.workflowRepository = workflowRepository;
        this.fileRepository = fileRepository;
        this.jobRepository = jobRepository;
    }

    public JobResponse createJob(CreateJobRequest request) {
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

        if (workflow.status() != WorkflowStatus.ACTIVE) {
            throw new JobValidationException(
                    "Workflow must be ACTIVE before creating a job"
            );
        }

        if (file.status() != FileStatus.AVAILABLE) {
            throw new JobValidationException(
                    "File must be AVAILABLE before creating a job"
            );
        }

        String idempotencyKey = generateIdempotencyKey(
                workflow.id(),
                file.id()
        );

        return jobRepository.findByIdempotencyKey(idempotencyKey)
                .map(JobResponse::from)
                .orElseGet(() -> {
                    Job job = Job.submit(
                            UUID.randomUUID(),
                            request.project_id(),
                            workflow.id(),
                            file.id(),
                            idempotencyKey
                    );

                    return JobResponse.from(jobRepository.save(job));
                });
    }

    public JobResponse queueJob(UUID id) {

        Job submittedJob = jobRepository.findById(id)
                .orElseThrow(() -> new JobNotFoundException(id));

        Job queuedJob = submittedJob.markQueued();

        return JobResponse.from(jobRepository.save(queuedJob));
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
}
