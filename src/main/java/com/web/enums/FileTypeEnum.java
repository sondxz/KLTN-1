package com.web.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum FileTypeEnum {
    IMAGE(1, "Hình ảnh"),
    VIDEO(2, "Video"),
    DOCUMENT(3, "Tài liệu");

    private final Integer type;
    private final String description;

    public static FileTypeEnum fromType(Integer type) {
        if (type == null) {
            return null;
        }
        for (FileTypeEnum fileType : FileTypeEnum.values()) {
            if (fileType.getType().equals(type)) {
                return fileType;
            }
        }
        return null;
    }
}
