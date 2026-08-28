package com.atlasflow.job.application.port;

import com.atlasflow.job.domain.Job;
import java.util.Optional;
import java.util.UUID;

public interface JobRepository {
    Optional<Job> findByIdempotencyKey(String idempotencyKey);
    Job save(Job job);

    Optional<Job> findById (UUID id);
}
