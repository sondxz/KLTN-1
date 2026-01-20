package com.web.dto;

import javax.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResearchDto {
    private Long id;

    @NotBlank(message = "Tiêu đề không được để trống")
    private String title;

    private String slug;
    private String abstractText;

    @NotBlank(message = "Nội dung không được để trống")
    private String content;

    private String authors;
    private String institution;
    private Integer publishedYear;
    private String journal;
    private String field;
    private Integer status;
    private Integer views;
    private Integer createdById;
    private String createdByName;
    private String createdAt;
    private String updatedAt;
//    private List<PlantDto> plants;
}
