package com.web.dto.request;

import com.web.dto.BaseObjectDto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DiseasesRequestDto extends BaseObjectDto {
    private String name;
    private String slug;
    private String description;
    private Long parentId;
}
