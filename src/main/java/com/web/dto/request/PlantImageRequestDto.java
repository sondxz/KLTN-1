package com.web.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PlantImageRequestDto {
    private Long plantId;
    private Long fileId;
    private Boolean isPrimary;
}
