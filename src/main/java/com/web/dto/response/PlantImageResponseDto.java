package com.web.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlantImageResponseDto {
    private Long id;
    private Long plantId;
    private Long fileId;
    private Boolean isPrimary;
}
