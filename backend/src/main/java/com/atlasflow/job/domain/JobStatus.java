package com.atlasflow.job.domain;

/**
 * Trạng thái nghiệp vụ của một yêu cầu chạy workflow.
 *
 * Luồng thông thường:
 * SUBMITTED -> QUEUED -> RUNNING -> SUCCEEDED hoặc FAILED
 */
public enum JobStatus {

    /** API đã nhận yêu cầu chạy job. */
    SUBMITTED,

    /** Job đã được đưa vào hàng đợi chờ worker nhận. */
    QUEUED,

    /** Job đang được xử lý. */
    RUNNING,

    /** Job đã hoàn thành thành công. */
    SUCCEEDED,

    /** Job thất bại sau khi xử lý hoặc hết số lần retry. */
    FAILED,

    /** User đã yêu cầu hủy nhưng hệ thống chưa hủy xong. */
    CANCEL_REQUESTED,

    /** Job đã được hủy thành công. */
    CANCELLED
}
