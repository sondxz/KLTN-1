package com.web.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCodeEnum {
    // Successful response
    SUCCESS(200, "Success"),

    // Standard HTTP errors
    UNAUTHORIZED(401, "Không được phép truy cập"),
    FORBIDDEN(403, "Truy cập bị cấm"),
    NOT_FOUND(404, "Không tìm thấy tài nguyên"),
    INTERNAL_SERVER_ERROR(500, "Lỗi hệ thống"),

    // Authentication/Authorization errors (401xxx)
    INVALID_TOKEN(401001, "Token không hợp lệ"),
    EXPIRED_TOKEN(401002, "Token đã hết hạn"),

    // Bad Request errors (400xxx)
    // -- User management errors (4001xx)
    EXISTED_USERNAME(400101, "Tên đăng nhập đã tồn tại"),
    EXISTED_EMAIL(400102, "Email đã được sử dụng"),
    CONFIRM_PASSWORD_MISMATCH(400103, "Mật khẩu xác nhận không khớp"),

    // -- Plant management errors (4002xx)
    PLANT_FAMILY_EXISTS(400201, "Họ thực vật đã tồn tại"),
    PLANT_EXISTS(400202, "Cây dược liệu đã tồn tại"),
    PLANT_NAME_EXISTS(400203, "Tên cây đã tồn tại"),
    INVALID_ENTITY_TYPE(400204, "Loại entity không hợp lệ"),

    // -- disease management errors (4003xx)
    DISEASE_EXISTS(400301, "Loại bệnh đã tồn tại"),

    //
    TITLE_EXISTS(400401, "Tiêu đề đã tồn tại"),

    // -- File management errors (4003xx)
    EMPTY_FILE(400301, "File không được để trống"),
    FILE_UPLOAD_FAILED(400302, "Không thể lưu file"),
    UNSUPPORTED_FILE_FORMAT(400303, "Định dạng file không được hỗ trợ"),
    INVALID_FILE(400304, "File không hợp lệ"),
    FILE_IO_ERROR(400305, "Lỗi khi xử lý file"),
    FILE_DATABASE_ERROR(400306, "Lỗi khi lưu thông tin media vào cơ sở dữ liệu"),
    FILE_TOO_LARGE(400307, "File vượt quá kích thước cho phép"),
    ;
    private final int errorCode;
    private final String message;
}
