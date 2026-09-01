package com.atlasflow.project.application;

import com.atlasflow.cache.CacheKeyFactory;
import com.atlasflow.cache.CacheService;
import com.atlasflow.project.api.ProjectResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Service
public class ProjectCacheService {

    private final CacheService cacheService;
    private final Duration ttl;

    public ProjectCacheService(
            CacheService cacheService,
            @Value("${app.cache.project.ttl-minutes:10}") long ttlMinutes
    ) {
        this.cacheService = cacheService;
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    public Optional<ProjectResponse> getProjectFromCache(UUID id) {
        String key = CacheKeyFactory.project(id);
        return cacheService.get(key, ProjectResponse.class);
    }

    public void putProjectToCache(ProjectResponse project) {
        String key = CacheKeyFactory.project(project.id());
        cacheService.put(key, project, ttl);
    }

    public void evictProjectFromCache(UUID id) {
        String key = CacheKeyFactory.project(id);
        cacheService.evict(key);
    }

    public Optional<String> tryAcquireLoadLock(UUID id) {
        return cacheService.tryAcquireLock(
                CacheKeyFactory.projectLock(id),
                Duration.ofSeconds(3)
        );
    }

    public void releaseLoadLock(UUID id, String token) {
        cacheService.releaseLock(
                CacheKeyFactory.projectLock(id),
                token
        );
    }
}
