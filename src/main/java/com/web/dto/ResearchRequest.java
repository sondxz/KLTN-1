package com.web.dto;

import com.web.entity.Research;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ResearchRequest {

    private Research research;

    private List<Long> plantId = new ArrayList<>();
    
    /**
     * Danh sách ID các Expert (chuyên gia) là tác giả của nghiên cứu
     * Nếu Expert có trong DB thì dùng ID này để link
     */
    private List<Long> expertIds = new ArrayList<>();
    
    /**
     * Text tác giả tự do (cho các tác giả không phải Expert trong DB)
     * Ví dụ: "Nguyễn Văn A, Trần Thị B"
     */
    private String authorsText;
}
