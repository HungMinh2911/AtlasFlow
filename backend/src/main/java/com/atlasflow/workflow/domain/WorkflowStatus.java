package com.atlasflow.workflow.domain;

public enum WorkflowStatus {

    /** Workflow đang được chỉnh sửa, chưa sẵn sàng chạy. */
    DRAFT,

    /** Workflow đã được kích hoạt và có thể được submit thành job. */
    ACTIVE,

    /** Workflow cũ đã ngừng sử dụng nhưng vẫn giữ để tra cứu. */
    ARCHIVED
}
