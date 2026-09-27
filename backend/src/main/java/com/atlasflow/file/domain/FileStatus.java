package com.atlasflow.file.domain;

public enum FileStatus {

    /**
     * File đang được nhận từ phía client.
     * Chưa được lưu hoàn tất nên chưa được phép đưa vào xử lý.
     */
    UPLOADING,

    /**
     * File đã được lưu thành công và có thể dùng làm input cho Job.
     */
    AVAILABLE,

    /**
     * File đang được một Workflow hoặc Job xử lý.
     */
    PROCESSING,

    /**
     * File đã xử lý xong thành công.
     */
    PROCESSED,

    /**
     * File bị lỗi trong quá trình upload hoặc xử lý.
     */
    FAILED,

    /**
     * File đã bị xóa về mặt nghiệp vụ.
     * Có thể vẫn còn dữ liệu vật lý trong storage chờ dọn dẹp.
     */
    DELETED
}
