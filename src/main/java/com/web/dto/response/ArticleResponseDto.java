package com.web.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArticleResponseDto extends BaseResponseDto {
    private Long id;
    private String title;
    private String slug;
    private String excerpt;
    private String content;
    private Long featuredImage;
    private Integer status = 1; // Default: DRAFT
    private Boolean isFeatured = false;
    private Boolean allowComments = true;
    private Long views;
    private Long authorId;
    private Long diseaseId;
    private LocalDateTime publishedAt;
}
