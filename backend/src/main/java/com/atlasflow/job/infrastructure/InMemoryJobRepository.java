package com.atlasflow.job.infrastructure;

import com.atlasflow.job.application.port.JobRepository;
import com.atlasflow.job.domain.Job;
import com.atlasflow.job.domain.JobStatus;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryJobRepository implements JobRepository {
    private final Map<UUID, Job> jobs = new ConcurrentHashMap<>();
    private final Map<String, UUID> jobIdsByIdempotencyKey =
            new ConcurrentHashMap<>();

    @Override
    public Optional<Job> findByIdempotencyKey(String idempotencyKey) {
        UUID jobId = jobIdsByIdempotencyKey.get(idempotencyKey);
        return jobId == null
                ? Optional.empty()
                : Optional.ofNullable(jobs.get(jobId));
    }

    @Override
    public Optional<Job> findByProjectIdAndIdempotencyKey(UUID projectId, String idempotencyKey) {
        return findByIdempotencyKey(idempotencyKey)
                .filter(job -> job.projectId().equals(projectId));
    }

    @Override
    public Optional<Job> insertIfAbsent(Job job) {
        if (job.idempotencyKey() != null && jobIdsByIdempotencyKey.putIfAbsent(job.idempotencyKey(), job.id()) != null) {
            return Optional.empty();
        }
        jobs.put(job.id(), job);
        return Optional.of(job);
    }

    @Override
    public Job save(Job job) {
        jobs.put(job.id(), job);
        jobIdsByIdempotencyKey.put(job.idempotencyKey(), job.id());
        return job;
    }

    @Override
    public Optional<Job> findById(UUID id) {
        return Optional.ofNullable(jobs.get(id));

    }

    @Override
    public synchronized int updateStatusToRunning(UUID jobId) {
        Job job = jobs.get(jobId);
        if (job == null || job.status() != JobStatus.QUEUED) {
            return 0;
        }

        jobs.put(jobId, job.startRun());
        return 1;
    }
}
