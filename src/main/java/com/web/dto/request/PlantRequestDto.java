package com.web.dto.request;

import javax.validation.constraints.NotBlank;

import com.web.entity.Plant;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class PlantRequestDto {

    private Plant plant;

    private List<String> images = new ArrayList<>();

    // Sử dụng List<Object> để nhận cả String và Long từ frontend
    private List<Object> diseasesIds = new ArrayList<>();
    
    /**
     * Flag để bỏ qua kiểm tra trùng lặp khi user đã xác nhận
     */
    private Boolean forceSubmit = false;
}
