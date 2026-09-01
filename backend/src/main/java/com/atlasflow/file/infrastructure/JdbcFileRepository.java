package com.atlasflow.file.infrastructure;

import com.atlasflow.file.application.port.FileRepository;
import com.atlasflow.file.domain.AtlasFile;
import com.atlasflow.file.domain.FileStatus;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

@Repository
@Primary
public class JdbcFileRepository implements FileRepository {

    private final JdbcClient jdbcClient;

    public JdbcFileRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    private AtlasFile mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new AtlasFile(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getString("file_name"),
                rs.getString("object_key"),
                rs.getString("checksum"),
                rs.getLong("size_bytes"),
                FileStatus.valueOf(rs.getString("status"))
        );
    }

    @Override
    public Optional<AtlasFile> findById(UUID id) {
        String sql = """
            SELECT id, project_id, file_name, object_key, checksum, size_bytes, status
            FROM files
            WHERE id = :id
        """;

        return jdbcClient.sql(sql)
                .param("id", id)
                .query(this::mapRow)
                .optional();
    }

    @Override
    public AtlasFile save(AtlasFile file) {
        String sql = """
            INSERT INTO files (id, project_id, file_name, object_key, checksum, size_bytes, status)
            VALUES (:id, :projectId, :fileName, :objectKey, :checksum, :sizeBytes, :status)
            ON CONFLICT (id) DO UPDATE
                SET checksum = EXCLUDED.checksum,
                    size_bytes = EXCLUDED.size_bytes,
                    status = EXCLUDED.status
        """;

        jdbcClient.sql(sql)
                .param("id", file.id())
                .param("projectId", file.projectId())
                .param("fileName", file.fileName())
                .param("objectKey", file.objectKey())
                .param("checksum", file.checksum())
                .param("sizeBytes", file.sizeBytes())
                .param("status", file.status().name())
                .update();

        return file;
    }
}
