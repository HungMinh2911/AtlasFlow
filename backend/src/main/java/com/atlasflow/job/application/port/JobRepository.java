package com.atlasflow.job.application.port;

import com.atlasflow.job.domain.Job;
import java.util.Optional;
import java.util.UUID;

public interface JobRepository {
    Optional<Job> findByIdempotencyKey(String idempotencyKey);

    default Optional<Job> findByProjectIdAndIdempotencyKey(UUID projectId, String idempotencyKey) {
        return findByIdempotencyKey(idempotencyKey)
                .filter(job -> job.projectId().equals(projectId));
    }
    Job save(Job job);

    default Optional<Job> insertIfAbsent(Job job) {
        return Optional.of(save(job));
    }

    Optional<Job> findById (UUID id);

    int updateStatusToRunning(UUID jobId);

}
