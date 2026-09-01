package com.atlasflow.job.api;

import com.atlasflow.job.application.JobApplicationService;
import com.atlasflow.job.domain.JobNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(JobController.class)
class JobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JobApplicationService jobApplicationService;

    @Test
    void createJobShouldReturnAcceptedAndRequireIdempotencyKey() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID workflowId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();

        when(jobApplicationService.createJob(any(CreateJobRequest.class), eq("request-001")))
                .thenReturn(new JobResponse(
                        UUID.randomUUID(), projectId, workflowId, fileId,
                        "SUBMITTED", "request-001", LocalDateTime.now()
                 ));

        mockMvc.perform(post("/api/v1/jobs")
                        .header("Idempotency-Key", "request-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "project_id": "%s",
                                  "workflow_id": "%s",
                                  "file_id": "%s"
                                }
                                """.formatted(projectId, workflowId, fileId)))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    void createJobShouldRejectMissingIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/v1/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "project_id": "%s",
                                  "workflow_id": "%s",
                                  "file_id": "%s"
                                }
                                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    void getJobShouldReturnJobAndRequestIdWhenExists() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID workflowId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();

        when(jobApplicationService.getJob(jobId))
                .thenReturn(new JobResponse(
                        jobId, projectId, workflowId, fileId,
                        "SUBMITTED", "request-001", LocalDateTime.now()
                ));

        mockMvc.perform(get("/api/v1/jobs/" + jobId))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.id").value(jobId.toString()))
                .andExpect(jsonPath("$.project_id").value(projectId.toString()))
                .andExpect(jsonPath("$.workflow_id").value(workflowId.toString()))
                .andExpect(jsonPath("$.file_id").value(fileId.toString()))
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
    }

    @Test
    void getJobShouldReturn404WhenNotExists() throws Exception {
        UUID jobId = UUID.randomUUID();

        when(jobApplicationService.getJob(jobId))
                .thenThrow(new JobNotFoundException(jobId));

        mockMvc.perform(get("/api/v1/jobs/" + jobId))
                .andExpect(status().isNotFound())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.code").value("JOB_NOT_FOUND"));
    }

    @Test
    void createJobShouldReturnConflictWhenIdempotencyKeyReusedWithDifferentPayload() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID workflowId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();

        when(jobApplicationService.createJob(any(CreateJobRequest.class), eq("reused-key")))
                .thenThrow(new com.atlasflow.job.domain.IdempotencyKeyReusedException("reused-key"));

        mockMvc.perform(post("/api/v1/jobs")
                        .header("Idempotency-Key", "reused-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "project_id": "%s",
                                  "workflow_id": "%s",
                                  "file_id": "%s"
                                }
                                """.formatted(projectId, workflowId, fileId)))
                .andExpect(status().isConflict())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }
}
