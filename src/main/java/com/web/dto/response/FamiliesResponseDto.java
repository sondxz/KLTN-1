package com.web.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.web.dto.BaseObjectDto;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class FamiliesResponseDto extends BaseObjectDto {
    private Long id;
    private String name;
    private String slug;
    private String description;
    private Long parentId;
}
