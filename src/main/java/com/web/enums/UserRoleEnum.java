package com.web.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum UserRoleEnum {
    ADMIN(1, "Quản trị viên hệ thống"),
    EDITOR(2, "Biên tập viên"),
    EPXERT(3, "Chuyên gia dược liệu"),
    USER(4, "Người dùng thông thường"),
    ;

    private final Integer type;
    private final String value;

    public static UserRoleEnum getByType(Integer type) {
        for (UserRoleEnum e : UserRoleEnum.values()) {
            if (e.type.equals(type)) {
                return e;
            }
        }
        return null;
    }
}
