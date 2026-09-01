package com.atlasflow.workflow.infrastructure;

import com.atlasflow.workflow.application.port.WorkflowRepository;
import com.atlasflow.workflow.domain.Workflow;
import com.atlasflow.workflow.domain.WorkflowStatus;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

@Repository
@Primary
public class JdbcWorkflowRepository implements WorkflowRepository {

    private final JdbcClient jdbcClient;

    public JdbcWorkflowRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }


    private Workflow mapRow (ResultSet rs , int rowNum) throws SQLException {
        return new Workflow(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getInt("version"),
                WorkflowStatus.valueOf(rs.getString("status"))

        );

    }
    @Override
    public Optional<Integer> findMaxVersionByProjectId(UUID project_Id){

        return jdbcClient.sql("""
            SELECT max(version) 
            FROM workflows WHERE project_id = :projectId
            """).param("projectId",project_Id)
                .query(Integer.class)
                .optional();
    }

    @Override
    public  Optional<Workflow> findById(UUID id){

        return jdbcClient.sql("""
            SELECT id, project_id, version, status 
            FROM workflows
            WHERE id = :id
        
        """)
                .param("id",id)
                .query(this::mapRow)
                .optional();
    }

    @Override
    public Workflow save(Workflow workflow){

         jdbcClient.sql("""
            INSERT INTO workflows (id, project_id, version, status)
            VALUES (:id, :project_id, :version, :status)
            ON CONFLICT (id) DO UPDATE 
                SET version = EXCLUDED.version,
                 status = EXCLUDED.status
        
        """)
                .param("id",workflow.id())
                .param("project_id",workflow.project_id())
                .param("version",workflow.version())
                .param("status",workflow.status().name())
                .update();
         return  workflow;

    }
}
