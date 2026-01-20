package com.web.dto;

import javax.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommentDto {
    private Long id;

    @NotBlank(message = "Nội dung không được để trống")
    private String content;

    private Integer userId;
    private String userName;
    private Integer articleId;
    private String articleTitle;
    private Integer parentId;
    private Integer status;
    private String createdAt;
    private String updatedAt;
    private List<CommentDto> replies;
}
