package com.web.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum PlantStatusEnum {
    DRAFT(1, "Bản nháp"),
    PENDING(2, "Chờ duyệt"),
    PUBLISHED(3, "Đã xuất bản"),
    ARCHIVED(4, "Lưu trữ"),
    REJECTED(5, "Từ chối")
    ;
    
    private final Integer type;
    private final String value;
}
