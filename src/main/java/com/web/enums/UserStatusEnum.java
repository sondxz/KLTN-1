package com.web.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum UserStatusEnum {
    ACTIVE(1, "Hoạt động"),
    PENDING(2, "Chờ duyệt"),
    BLOCKED(3, "Bị chặn"),
    ;
    
    private final Integer type;
    private final String value;
}
