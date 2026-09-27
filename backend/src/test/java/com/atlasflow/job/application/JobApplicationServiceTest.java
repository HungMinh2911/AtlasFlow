package com.atlasflow.job.application;

import com.atlasflow.file.application.port.FileRepository;
import com.atlasflow.file.domain.AtlasFile;
import com.atlasflow.file.domain.FileStatus;
import com.atlasflow.job.api.CreateJobRequest;
import com.atlasflow.job.api.JobResponse;
import com.atlasflow.job.application.port.JobRepository;
import com.atlasflow.job.domain.Job;
import com.atlasflow.job.domain.JobStatus;
import com.atlasflow.job.domain.JobValidationException;
import com.atlasflow.outbox_events.application.OutboxEventsApplicationService;
import com.atlasflow.project.application.port.ProjectRepository;
import com.atlasflow.project.domain.Project;
import com.atlasflow.workflow.application.port.WorkflowRepository;
import com.atlasflow.workflow.domain.Workflow;
import com.atlasflow.workflow.domain.WorkflowStatus;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobApplicationServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private WorkflowRepository workflowRepository;

    @Mock
    private FileRepository fileRepository;

    @Mock
    private JobRepository jobRepository;

    @Mock
    private OutboxEventsApplicationService outboxEventsApplicationService;

    @Test
    void createJobShouldCreateSubmittedJobForValidInput() {
        JobApplicationService service = service();
        UUID projectId = UUID.randomUUID();
        UUID workflowId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();

        givenValidInput(projectId, workflowId, fileId);
        when(jobRepository.insertIfAbsent(any(Job.class)))
                .thenAnswer(invocation -> Optional.of(invocation.getArgument(0)));

        JobResponse response = service.createJob(
                new CreateJobRequest(projectId, workflowId, fileId)
        );

        assertEquals(JobStatus.SUBMITTED.name(), response.status());
        assertEquals(projectId, response.project_id());
        assertEquals(workflowId, response.workflow_id());
        assertEquals(fileId, response.file_id());
    }

    @Test
    void createJobShouldRejectWorkflowFromAnotherProject() {
        JobApplicationService service = service();
        UUID projectId = UUID.randomUUID();
        UUID otherProjectId = UUID.randomUUID();
        UUID workflowId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();

        when(projectRepository.findProjectById(projectId))
                .thenReturn(Optional.of(Project.create(
                        UUID.randomUUID(), projectId, "AtlasFlow"
                )));
        when(workflowRepository.findById(workflowId))
                .thenReturn(Optional.of(new Workflow(
                        workflowId,
                        otherProjectId,
                        1,
                        WorkflowStatus.PUBLISHED
                )));
        when(fileRepository.findById(fileId))
                .thenReturn(Optional.of(new AtlasFile(
                        fileId,
                        projectId,
                        "input.txt",
                        "objects/input.txt",
                        "checksum",
                        10,
                        FileStatus.AVAILABLE
                )));

        assertThrows(
                JobValidationException.class,
                () -> service.createJob(
                        new CreateJobRequest(projectId, workflowId, fileId)
                )
        );
    }

    @Test
    void createJobShouldReplayWhenIdempotencyKeyAndPayloadMatch() {
        JobApplicationService service = service();
        UUID projectId = UUID.randomUUID();
        UUID workflowId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        givenValidInput(projectId, workflowId, fileId);

        Job existing = new Job(
                UUID.randomUUID(), projectId, workflowId, fileId,
                JobStatus.SUBMITTED, "client-key", fingerprint(projectId, workflowId, fileId),
                java.time.LocalDateTime.now()
        );
        when(jobRepository.findByProjectIdAndIdempotencyKey(projectId, "client-key"))
                .thenReturn(Optional.of(existing));

        JobResponse response = service.createJob(
                new CreateJobRequest(projectId, workflowId, fileId), "client-key"
        );

        assertEquals(existing.id(), response.id());
    }

    @Test
    void createJobShouldRejectWhenIdempotencyKeyPayloadDiffers() {
        JobApplicationService service = service();
        UUID projectId = UUID.randomUUID();
        UUID workflowId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        givenValidInput(projectId, workflowId, fileId);

        Job existing = new Job(
                UUID.randomUUID(), projectId, workflowId, fileId,
                JobStatus.SUBMITTED, "client-key", "different-fingerprint",
                java.time.LocalDateTime.now()
        );
        when(jobRepository.findByProjectIdAndIdempotencyKey(projectId, "client-key"))
                .thenReturn(Optional.of(existing));

        assertThrows(
                com.atlasflow.job.domain.IdempotencyKeyReusedException.class,
                () -> service.createJob(
                        new CreateJobRequest(projectId, workflowId, fileId), "client-key"
                )
        );
    }

    @Test
    void getJobShouldReturnJobWhenExists() {
        JobApplicationService service = service();
        UUID jobId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID workflowId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();

        Job job = new Job(
                jobId, projectId, workflowId, fileId,
                JobStatus.SUBMITTED, "client-key", fingerprint(projectId, workflowId, fileId),
                java.time.LocalDateTime.now()
        );
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

        JobResponse response = service.getJob(jobId);

        assertEquals(jobId, response.id());
        assertEquals(projectId, response.project_id());
        assertEquals(workflowId, response.workflow_id());
        assertEquals(fileId, response.file_id());
        assertEquals(JobStatus.SUBMITTED.name(), response.status());
    }

    @Test
    void getJobShouldThrowJobNotFoundExceptionWhenNotExists() {
        JobApplicationService service = service();
        UUID jobId = UUID.randomUUID();

        when(jobRepository.findById(jobId)).thenReturn(Optional.empty());

        assertThrows(
                com.atlasflow.job.domain.JobNotFoundException.class,
                () -> service.getJob(jobId)
        );
    }

    private String fingerprint(UUID projectId, UUID workflowId, UUID fileId) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(
                    digest.digest((projectId + "|" + workflowId + "|" + fileId)
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8))
            );
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private JobApplicationService service() {
        return new JobApplicationService(
                projectRepository,
                workflowRepository,
                fileRepository,
                jobRepository,
                outboxEventsApplicationService
        );
    }

    private void givenValidInput(
            UUID projectId,
            UUID workflowId,
            UUID fileId
    ) {
        when(projectRepository.findProjectById(projectId))
                .thenReturn(Optional.of(Project.create(
                        UUID.randomUUID(), projectId, "AtlasFlow"
                )));
        when(workflowRepository.findById(workflowId))
                .thenReturn(Optional.of(new Workflow(
                        workflowId,
                        projectId,
                        1,
                        WorkflowStatus.PUBLISHED
                )));
        when(fileRepository.findById(fileId))
                .thenReturn(Optional.of(new AtlasFile(
                        fileId,
                        projectId,
                        "input.txt",
                        "objects/input.txt",
                        "checksum",
                        10,
                        FileStatus.AVAILABLE
                )));
    }
}
