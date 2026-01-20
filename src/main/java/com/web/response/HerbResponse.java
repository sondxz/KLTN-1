package com.web.response;

import com.web.enums.ErrorCodeEnum;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HerbResponse<T> {
    private int code;
    private String message;
    private T data;
    private long total;

    public HerbResponse(T data) {
        this.data = data;
        this.code = 200;
    }

    public HerbResponse(ErrorCodeEnum errorCode) {
        this.code = errorCode.getErrorCode();
        this.message = errorCode.getMessage();
    }

    public HerbResponse(T data, ErrorCodeEnum errorCode) {
        this.data = data;
        this.code = errorCode.getErrorCode();
        this.message = errorCode.getMessage();
    }

    public HerbResponse(T data, long total) {
        this.data = data;
        this.code = 200;
        this.total = total;
    }

    public HerbResponse(int code, T data, long total) {
        this.code = code;
        this.data = data;
        this.total = total;
    }

    public HerbResponse(String message) {
        this.message = message;
    }

    public HerbResponse(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
