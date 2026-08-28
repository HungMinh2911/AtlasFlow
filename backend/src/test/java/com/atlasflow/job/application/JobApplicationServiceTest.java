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

    @Test
    void createJobShouldCreateSubmittedJobForValidInput() {
        JobApplicationService service = service();
        UUID projectId = UUID.randomUUID();
        UUID workflowId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();

        givenValidInput(projectId, workflowId, fileId);
        when(jobRepository.findByIdempotencyKey(any(String.class)))
                .thenReturn(Optional.empty());
        when(jobRepository.save(any(Job.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

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
                        WorkflowStatus.ACTIVE
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

    private JobApplicationService service() {
        return new JobApplicationService(
                projectRepository,
                workflowRepository,
                fileRepository,
                jobRepository
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
                        WorkflowStatus.ACTIVE
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
