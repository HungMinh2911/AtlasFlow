package com.atlasflow.job.execution.infrastructure;

import com.atlasflow.job.execution.application.port.JobExcutionIRepository;
import com.atlasflow.job.execution.domain.JobExcution;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryJobExcutionRepository implements JobExcutionIRepository {

    private final Map<UUID, JobExcution> jobExcutionMap = new ConcurrentHashMap<>();

    @Override
    public Optional<JobExcution> findById(UUID executionId) {
        return Optional.ofNullable(jobExcutionMap.get(executionId));
    }

    @Override
    public int max_attempt(UUID id){
        return jobExcutionMap.values().stream()
                .filter(excution -> excution.jobId().equals(id))
                .map(JobExcution::attempt)
                .max(Integer::compareTo)
                .orElse(0)+1;
    }

    @Override
    public JobExcution save (JobExcution jobExcution){
        jobExcutionMap.put(jobExcution.id(),jobExcution);

        return jobExcution;
    }

}
