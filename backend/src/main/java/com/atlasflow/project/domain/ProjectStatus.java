package com.atlasflow.project.domain;

public enum ProjectStatus {

    /** Project đang hoạt động và có thể nhận workflow/job. */
    ACTIVE,

    /** Project tạm dừng; không nên nhận xử lý mới. */
    SUSPENDED,

    /** Project đã lưu trữ; chỉ dùng để tra cứu, không xử lý mới. */
    ARCHIVED
}
