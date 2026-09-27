package com.atlasflow.workflow.step.infrastructure;


import com.atlasflow.workflow.step.application.port.WorkflowStepRepository;
import com.atlasflow.workflow.step.domain.WorkflowStep;
import com.atlasflow.workflow.step.domain.WorkflowStepType;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

@Repository
@Primary
public class JdbcWorkflowStepRepository implements WorkflowStepRepository {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public JdbcWorkflowStepRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    private WorkflowStep mapRow (ResultSet rs , int rowNum) throws SQLException {
        String rawJson = rs.getString("config_json");
        JsonNode configNode = null;

        if (rawJson != null){
            try {
                configNode = objectMapper.readTree(rawJson);
            } catch (Exception e){
                throw new SQLException("Failed to parse config_json",e);
            }
        }

        return new WorkflowStep(
                rs.getObject("id", UUID.class),
                rs.getObject("workflow_id", UUID.class),
                rs.getInt("position"),
                WorkflowStepType.valueOf(rs.getString("task_type")),
                configNode
        );

    }

    @Override
    public boolean existsByWorkflowIdAndPosition(UUID workflowId, int position) {
        String sql = """
            SELECT EXISTS (
                SELECT 1 FROM workflow_steps 
                WHERE workflow_id = :workflowId AND position = :position
            )
        """;

        return Boolean.TRUE.equals(
                jdbcClient.sql(sql)
                        .param("workflowId", workflowId)
                        .param("position", position)
                        .query(Boolean.class)
                        .single()
        );
    }

    @Override
    public List<WorkflowStep> getListWorkflowStepByWorkflowId(UUID workflowId) {
        String sql = """
            SELECT id, workflow_id, position, task_type, config_json
            FROM workflow_steps
            WHERE workflow_id = :workflowId 
            ORDER BY position ASC
        """;

        return jdbcClient.sql(sql)
                .param("workflowId", workflowId)
                .query(this::mapRow)
                .list();
    }

    @Override
    public WorkflowStep save(WorkflowStep workflowStep) {
        String configJsonString = workflowStep.configJson() != null 
                ? workflowStep.configJson().toString() 
                : "{}";

        String sql = """
            INSERT INTO workflow_steps (id, workflow_id, position, task_type, config_json)
            VALUES (:id, :workflow_id, :position, :task_type, :configJson::jsonb)
            ON CONFLICT (id) DO UPDATE
                SET position = EXCLUDED.position,
                    task_type = EXCLUDED.task_type,
                    config_json = EXCLUDED.config_json
        """;

        jdbcClient.sql(sql)
                .param("id", workflowStep.id())
                .param("workflow_id", workflowStep.workflowId())
                .param("position", workflowStep.position())
                .param("task_type", workflowStep.taskType().name())
                .param("configJson", configJsonString)
                .update();

        return workflowStep;
    }
}

//GHI (Java ➔ PostgreSQL):    JsonNode  ──(.toString())──>  String JSON  ──(:config::jsonb)──>  PostgreSQL JSONB
//ĐỌC (PostgreSQL ➔ Java):    PostgreSQL JSONB  ──(rs.getString)──>  String JSON  ──(objectMapper.readTree)──>  JsonNode
