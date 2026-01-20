package com.web.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BaseResponseDto {
    private Long id;
    private String createdAt;
    private String updatedAt;
    private String createdBy;
    private String updatedBy;
}
