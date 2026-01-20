package com.web.dto;

import javax.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArticleDto {
    private Long id;

    @NotBlank(message = "Tiêu đề không được để trống")
    private String title;

    private String slug;
    private String excerpt;

    @NotBlank(message = "Nội dung không được để trống")
    private String content;

    private Integer featuredImageId;
    private MediaDto featuredImage;
    private Integer status;
    private Boolean isFeatured;
    private Boolean allowComments;
    private Integer views;
    private Integer authorId;
    private String authorName;
    private Integer categoryId;
    private String categoryName;
    private String publishedAt;
    private String createdAt;
    private String updatedAt;

    /*
    private List<TagDto> tags;
    private List<PlantDto> plants;
    private Integer commentCount;

     */
}
