package com.web.dto;

import javax.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategoryDto {
    private Long id;

    @NotBlank(message = "Tên danh mục không được để trống")
    private String name;

    private String slug;
    private String description;
    private Integer parentId;
    private String parentName;
    private List<CategoryDto> children;
    private String createdAt;
}
