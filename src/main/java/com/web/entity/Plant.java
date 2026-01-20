package com.web.entity;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.web.enums.PlantStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "plants")
public class Plant extends BaseEntity {

    private String name;

    private String scientificName;

    @Column(nullable = false, unique = true)
    private String slug;

    /**
     * Chi (genus) mà cây dược liệu thuộc về
     */
    private String genus;

    @Column(name = "other_names")
    private String otherNames;

    /**
     * Các bộ phận được sử dụng của cây dược liệu (ví dụ: lá, rễ, hoa)
     */
    @Column(name = "parts_used")
    private String partsUsed;

    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Đặc điểm hình thái thực vật học của cây dược liệu
     */
    @Column(name = "botanical_characteristics", columnDefinition = "TEXT")
    private String botanicalCharacteristics;

    /**
     * Thành phần hóa học của cây dược liệu
     */
    @Column(name = "chemical_composition", columnDefinition = "TEXT")
    private String chemicalComposition;

    /**
     * Phân bố địa lý của cây dược liệu
     */
    @Column(columnDefinition = "TEXT")
    private String distribution;

    /**
     * Độ cao (altitude) nơi cây dược liệu sinh trưởng
     */
    @Column(name = "altitude")
    private String altitude;

    /**
     * Mùa thu hoạch của cây dược liệu
     */
    @Column(name = "harvest_season")
    private String harvestSeason;

    /**
     * Đặc điểm sinh thái của cây dược liệu
     */
    @Column(columnDefinition = "TEXT")
    private String ecology;

    /**
     * Công dụng y học của cây dược liệu
     */
    @Column(name = "medicinal_uses", columnDefinition = "TEXT")
    private String medicinalUses;

    /**
     * Các chỉ định sử dụng của cây dược liệu
     */
    @Column(columnDefinition = "TEXT")
    private String indications;

    /**
     * Các chống chỉ định khi sử dụng cây dược liệu
     */
    @Column(columnDefinition = "TEXT")
    private String contraindications;

    /**
     * Liều lượng sử dụng cây dược liệu
     */
    @Column(columnDefinition = "TEXT")
    private String dosage;

    /**
     * Các bài thuốc dân gian sử dụng cây dược liệu
     */
    @Column(name = "folk_remedies", columnDefinition = "TEXT")
    private String folkRemedies;

    /**
     * Các tác dụng phụ khi sử dụng cây dược liệu
     */
    @Column(name = "side_effects", columnDefinition = "TEXT")
    private String sideEffects;

    // Thân cây
    @Column(length = 500)
    private String stem;

    // Lá cây
    @Column(length = 500)
    private String leaf;

    // Hoa
    @Column(length = 500)
    private String flower;

    // Quả hoặc hạt
    @Column(length = 500)
    private String fruitOrSeed;

    // Rễ
    @Column(length = 500)
    private String root;

    private String linkDocument;

    private String image;

    /**
     * Nguồn tài liệu tham khảo (references, publication...)
     */
    @Column(columnDefinition = "TEXT")
    private String source;
    
    // Lombok @Data sẽ tự động generate getSource() và setSource()


    @Enumerated(EnumType.STRING)
    private PlantStatus plantStatus;

    private Boolean featured = false;

    @Column(name = "view_count")
    private Long viewCount = 0L;

    @ManyToOne
    private Families families;

    @OneToMany(mappedBy = "plant", cascade = CascadeType.REMOVE)
    @JsonManagedReference
    private List<PlantMedia> plantMedia = new ArrayList<>();

    @OneToMany(mappedBy = "plant", cascade = CascadeType.REMOVE)
    @JsonManagedReference
    private List<PlantDiseases> plantDiseases = new ArrayList<>();

    @JsonProperty("color")
    private String getColor(){
        // Phòng null để không gây NPE khi serialize JSON
        return this.plantStatus != null ? this.plantStatus.getColor() : "#6c757d";
    }

    @JsonProperty("statusLabel")
    private String getLabelStatus(){
        // Phòng null để không gây NPE khi serialize JSON
        return this.plantStatus != null ? this.plantStatus.getLabel() : "Không xác định";
    }
}
