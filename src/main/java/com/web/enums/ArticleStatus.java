package com.web.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ArticleStatus {

    BAN_NHAP("Bản nháp", "#6c757d"),
    CHO_DUYET("Chờ duyệt", "#ffc107"),
    DA_XUAT_BAN("Đã xuất bản", "#28a745"),
    LUU_TRU("Lưu trữ", "#17a2b8"),
    TU_CHOI("Từ chối", "#dc3545");

    private final String label;
    private final String color;
}
