package com.atlasflow.cache;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class CacheKeyFactoryTest {

    @Test
    void shouldCreateProjectKeyWithVersion() {

        UUID projectId = UUID.randomUUID();

        String key = CacheKeyFactory.project(projectId);

        assertThat(key)
                .isEqualTo("atlasflow:project:" + projectId + ":v1");
    }

    @Test
    void shouldCreateWorkflowKeyWithVersion() {
        UUID workflowId = UUID.randomUUID();

        String key = CacheKeyFactory.workflow(workflowId);

        assertThat(key)
                .isEqualTo("atlasflow:workflow:" + workflowId + ":v1");
    }
}
