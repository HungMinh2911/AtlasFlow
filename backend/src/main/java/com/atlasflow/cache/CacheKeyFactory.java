package com.atlasflow.cache;

import java.util.UUID;

public class CacheKeyFactory {

    private static final String PREFIX = "atlasflow";

    private CacheKeyFactory() {
        // Utility class
    }

    public static String project(UUID projectId) {
        return PREFIX + ":project:" + projectId + ":v1";
    }

    public static String workflow(UUID workflowId) {
        return PREFIX + ":workflow:" + workflowId + ":v1";
    }

    public static String projectLock(UUID projectId) {
        return PREFIX + ":lock:project:" + projectId + ":v1";
    }

    public static String workflowLock(UUID workflowId) {
        return PREFIX + ":lock:workflow:" + workflowId + ":v1";
    }
}

