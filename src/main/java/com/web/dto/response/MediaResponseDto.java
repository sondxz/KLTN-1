package com.web.dto.response;

import com.web.enums.FileTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaResponseDto {
    private Long id;
    private String fileName;
    private String filePath;
    private String urlFile;
    private Integer fileType;
    private String fileTypeName; // Tên loại file (Hình ảnh, Video, Tài liệu)
    private Integer fileSize;
    private String fileSizeFormatted; // Kích thước định dạng (KB, MB)
    private String altText;
    private LocalDateTime createdAt;
    private String createdBy;

    public String getFileTypeName() {
        var type = FileTypeEnum.fromType(fileType);
        return type != null ? type.getDescription() : "Không xác định";
    }

    public String getFileSizeFormatted() {
        if (fileSize == null) return "0 B";

        final String[] units = new String[] { "B", "KB", "MB", "GB", "TB" };
        int digitGroups = (int) (Math.log10(fileSize) / Math.log10(1024));

        return String.format("%.1f %s", fileSize / Math.pow(1024, digitGroups), units[digitGroups]);
    }
}
