package com.atlasflow.project.api;

import com.atlasflow.project.application.ProjectApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProjectController.class)
class ProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectApplicationService projectApplicationService;

    @Test
    void createProjectShouldRejectBlankName() throws Exception {
        mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organization_id": "%s",
                                  "name": "   "
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(header().exists("X-Request-Id"));

        verifyNoInteractions(projectApplicationService);
    }

    @Test
    void getProjectsShouldReturnProjectList() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();

        when(projectApplicationService.getProjects())
                .thenReturn(List.of(new ProjectResponse(
                        projectId,
                        organizationId,
                        "AtlasFlow",
                        "ACTIVE",
                        LocalDateTime.now()
                )));

        mockMvc.perform(get("/api/v1/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(projectId.toString()))
                .andExpect(jsonPath("$[0].name").value("AtlasFlow"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }
}
