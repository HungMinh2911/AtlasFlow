package com.atlasflow.workflow.application;

import com.atlasflow.cache.CacheKeyFactory;
import com.atlasflow.cache.CacheService;
import com.atlasflow.workflow.api.WorkflowDetailResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Service
public class WorkflowCacheService {

    private final CacheService cacheService;
    private final Duration ttl;

    public WorkflowCacheService(
            CacheService cacheService,
            @Value("${app.cache.workflow.ttl-minutes:10}") long ttlMinutes
    ) {
        this.cacheService = cacheService;
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    public Optional<WorkflowDetailResponse> getWorkflowFromCache(UUID id) {
        String key = CacheKeyFactory.workflow(id);
        return cacheService.get(key, WorkflowDetailResponse.class);
    }

    public void putWorkflowToCache(WorkflowDetailResponse workflow) {
        String key = CacheKeyFactory.workflow(workflow.id());
        cacheService.put(key, workflow, ttl);
    }

    public void evictWorkflowFromCache(UUID id) {
        String key = CacheKeyFactory.workflow(id);
        cacheService.evict(key);
    }

    public Optional<String> tryAcquireLoadLock(UUID id) {
        return cacheService.tryAcquireLock(
                CacheKeyFactory.workflowLock(id),
                Duration.ofSeconds(3)
        );
    }

    public void releaseLoadLock(UUID id, String token) {
        cacheService.releaseLock(
                CacheKeyFactory.workflowLock(id),
                token
        );
    }

}
