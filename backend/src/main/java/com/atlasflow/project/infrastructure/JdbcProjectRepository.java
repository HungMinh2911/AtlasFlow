package com.atlasflow.project.infrastructure;

import com.atlasflow.project.application.port.ProjectRepository;
import com.atlasflow.project.domain.Project;
import com.atlasflow.project.domain.ProjectStatus;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Primary
public class JdbcProjectRepository implements ProjectRepository {

    private final JdbcClient jdbcClient;

    public JdbcProjectRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }


    private Project mapRow(ResultSet rs, int rowNum) throws SQLException{
        Timestamp timestamp = rs.getTimestamp("created_at");

        return new Project(
                rs.getObject("id", UUID.class),
                rs.getObject("organization_id", UUID.class),
                rs.getString("name"),
                ProjectStatus.valueOf(rs.getString("status")),
                timestamp != null
                        ? timestamp.toInstant().atZone(ZoneOffset.UTC).toLocalDateTime()
                        : null
        );
    }

    @Override
    public boolean existsByOrganizationIdAndName(UUID organizationId, String name){
        return jdbcClient.sql("""
            SELECT EXISTS(
                SELECT 1 
                FROM projects
                WHERE organization_id = :orgId
                    AND LOWER(name) = LOWER(:name)
                
            )
        """)
                .param("orgId",organizationId)
                .param("name", name)
                .query(Boolean.class)
                .single();
    }

    @Override
    public Optional<Project> findProjectById(UUID id){

        String sql = """
                SELECT id, organization_id, name, status, created_at
                FROM projects
                WHERE id = :id 
                """;

        return jdbcClient.sql(sql)
                .param("id",id)
                .query(this::mapRow)
                .optional();
    }

    @Override
    public List<Project> findAll(){

        String sql = "SELECT id, organization_id, name, status, created_at FROM projects ORDER BY created_at ASC";
        return jdbcClient.sql(sql)
                .query(this::mapRow)
                .list();
    }

    @Override
    public Project save(Project project){
        String sql = """
                INSERT INTO projects (id, organization_id, name, status, created_at)
                VALUES (:id, :orgId, :name, :status, :createdAt)
                ON CONFLICT (id) DO UPDATE
                SET name = EXCLUDED.name,
                    status = EXCLUDED.status
                """;

        jdbcClient.sql(sql)
                .param("id",project.id())
                .param("orgId", project.organization_id())
                .param("name", project.name())
                .param("status", project.status().name())
                .param("createdAt", project.created_at() != null ? Timestamp.valueOf(project.created_at()) : null)
                .update();

        return  project;
    }


}
