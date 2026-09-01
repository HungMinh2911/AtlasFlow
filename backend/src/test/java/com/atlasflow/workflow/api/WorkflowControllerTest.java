package com.atlasflow.workflow.api;

import com.atlasflow.workflow.application.WorkflowApplicationService;
import com.atlasflow.workflow.domain.WorkflowNotFoundException;
import com.atlasflow.workflow.step.api.WorkflowStepResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WorkflowController.class)
class WorkflowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WorkflowApplicationService workflowApplicationService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void getWorkflowShouldReturnDetailWithOrderedStepsAndRequestId() throws Exception {
        UUID workflowId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID step1Id = UUID.randomUUID();
        UUID step2Id = UUID.randomUUID();

        var step1 = new WorkflowStepResponse(step1Id, workflowId, 1, "VALIDATE_FILE", objectMapper.readTree("{\"allowed_extensions\":[\"txt\"]}"));
        var step2 = new WorkflowStepResponse(step2Id, workflowId, 2, "EXTRACT_TEXT", objectMapper.readTree("{\"language\":\"en\"}"));

        var response = new WorkflowDetailResponse(
                workflowId,
                projectId,
                1,
                "PUBLISHED",
                List.of(step1, step2)
        );

        when(workflowApplicationService.getWorkflow(workflowId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/workflows/" + workflowId))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.id").value(workflowId.toString()))
                .andExpect(jsonPath("$.project_id").value(projectId.toString()))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.steps[0].position").value(1))
                .andExpect(jsonPath("$.steps[0].task_type").value("VALIDATE_FILE"))
                .andExpect(jsonPath("$.steps[1].position").value(2))
                .andExpect(jsonPath("$.steps[1].task_type").value("EXTRACT_TEXT"));
    }

    @Test
    void getWorkflowShouldReturn404WhenNotFound() throws Exception {
        UUID workflowId = UUID.randomUUID();

        when(workflowApplicationService.getWorkflow(workflowId))
                .thenThrow(new WorkflowNotFoundException(workflowId));

        mockMvc.perform(get("/api/v1/workflows/" + workflowId))
                .andExpect(status().isNotFound())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.code").value("WORKFLOW_NOT_FOUND"));
    }
}
