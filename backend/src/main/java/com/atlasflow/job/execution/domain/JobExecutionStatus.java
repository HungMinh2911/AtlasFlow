package com.atlasflow.job.execution.domain;

/**
 * Trạng thái của một lần chạy cụ thể của Job.
 * Một Job có thể có nhiều JobExecution khi được retry.
 */
public enum JobExecutionStatus {
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED
}
