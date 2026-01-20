package com.web.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class PlantSearch {

    private String search; // Full text search - tìm tất cả các trường

    private String nameSearch; // Search chỉ theo tên cây

    private List<Long> familiesId = new ArrayList<>();

    private List<Long> diseases = new ArrayList<>();
}
