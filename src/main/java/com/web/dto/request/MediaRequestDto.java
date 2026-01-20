package com.web.dto.request;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaRequestDto {
    @NotNull(message = "File không được để trống")
    private MultipartFile file;

    private Integer fileType; // Nếu null, sẽ được xác định tự động dựa trên ContentType

    @Size(max = 255, message = "Văn bản thay thế không được vượt quá 255 ký tự")
    private String altText;

    // Thêm thuộc tính tùy chọn để kiểm soát việc ghi đè file
    private Boolean overrideIfExists;

    // Có thể thêm mô tả cho file (tùy chọn)
    @Size(max = 500, message = "Mô tả không được vượt quá 500 ký tự")
    private String description;

    private Long plantId;

    private Long articleId;

    private Boolean isFeatured;
}
