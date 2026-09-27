package com.atlasflow.job.execution.infrastructure;

import com.atlasflow.job.execution.application.port.JobExecutionRepository;
import com.atlasflow.job.execution.domain.JobExecution;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryJobExecutionRepository implements JobExecutionRepository {

    private final Map<UUID, JobExecution> jobExcutionMap = new ConcurrentHashMap<>();

    @Override
    public Optional<JobExecution> findById(UUID executionId) {
        return Optional.ofNullable(jobExcutionMap.get(executionId));
    }

    @Override
    public int max_attempt(UUID id){
        return jobExcutionMap.values().stream()
                .filter(excution -> excution.jobId().equals(id))
                .map(JobExecution::attempt)
                .max(Integer::compareTo)
                .orElse(0)+1;
    }

    @Override
    public JobExecution save (JobExecution jobExcution){
        jobExcutionMap.put(jobExcution.id(),jobExcution);

        return jobExcution;
    }

    @Override
    public JobExecution saveIfAbsent(JobExecution jobExecution) {
        Optional<JobExecution> existing =
                findByJobIdAndAttempt(jobExecution.jobId(), jobExecution.attempt());

        if (existing.isPresent()) {
            return existing.get();
        }

        jobExcutionMap.put(jobExecution.id(), jobExecution);
        return jobExecution;
    }

    @Override
    public boolean findByJobId(UUID jobId) {
        return jobExcutionMap.values().stream()
                .anyMatch(execution -> execution.jobId().equals(jobId));
    }

    @Override
    public Optional<JobExecution> findLatestByJobId(UUID jobId) {
        return jobExcutionMap.values().stream()
                .filter(execution -> execution.jobId().equals(jobId))
                .max(java.util.Comparator.comparingInt(JobExecution::attempt));
    }

    @Override
    public Optional<JobExecution> findByJobIdAndAttempt(UUID jobId, int attempt) {
        return jobExcutionMap.values().stream()
                .filter(execution -> execution.jobId().equals(jobId))
                .filter(execution -> execution.attempt() == attempt)
                .findFirst();
    }

}
