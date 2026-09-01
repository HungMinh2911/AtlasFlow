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

}
