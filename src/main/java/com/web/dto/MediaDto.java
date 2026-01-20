package com.web.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MediaDto {
    private Long id;
    private String fileName;
    private String filePath;
    private Integer fileType;
    private Integer fileSize;
    private String altText;
    private Integer uploadedById;
    private String uploadedByUsername;
    private String createdAt;
    private String fileUrl;
}
