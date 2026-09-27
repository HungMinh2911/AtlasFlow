package com.atlasflow.project.application;

import com.atlasflow.cache.CacheKeyFactory;
import com.atlasflow.cache.CacheService;
import com.atlasflow.project.api.ProjectResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectCacheServiceTest {

    @Mock
    private CacheService cacheService;

    private ProjectCacheService projectCacheService;

    @BeforeEach
    void setUp() {
        projectCacheService = new ProjectCacheService(cacheService, 10L);
    }

    @Test
    void getProjectFromCacheShouldRetrieveUsingCacheKeyFactory() {
        UUID id = UUID.randomUUID();
        String expectedKey = CacheKeyFactory.project(id);
        ProjectResponse response = new ProjectResponse(
                id, UUID.randomUUID(), "Test Project", "ACTIVE", LocalDateTime.now()
        );

        when(cacheService.get(expectedKey, ProjectResponse.class))
                .thenReturn(Optional.of(response));

        Optional<ProjectResponse> result = projectCacheService.getProjectFromCache(id);

        assertTrue(result.isPresent());
        assertEquals("Test Project", result.get().name());
        verify(cacheService).get(expectedKey, ProjectResponse.class);
    }

    @Test
    void putProjectToCacheShouldStoreWithTtl() {
        UUID id = UUID.randomUUID();
        String expectedKey = CacheKeyFactory.project(id);
        ProjectResponse response = new ProjectResponse(
                id, UUID.randomUUID(), "Test Project", "ACTIVE", LocalDateTime.now()
        );

        projectCacheService.putProjectToCache(response);

        verify(cacheService).put(expectedKey, response, Duration.ofMinutes(10));
    }

    @Test
    void evictProjectFromCacheShouldDeleteKey() {
        UUID id = UUID.randomUUID();
        String expectedKey = CacheKeyFactory.project(id);

        projectCacheService.evictProjectFromCache(id);

        verify(cacheService).evict(expectedKey);
    }
}
