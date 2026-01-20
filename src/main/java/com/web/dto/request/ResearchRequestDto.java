package com.web.dto.request;

import javax.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResearchRequestDto {
    @NotBlank(message = "Tiêu đề không được để trống")
    private String title;
    private String slug;
    private String abstractText;
    private String content;
    private String authors;
    private String institution;
    private Integer publishedYear;
    private String journal;
    private String field;
    private Integer status = 2;
    private Integer views = 0;
}
