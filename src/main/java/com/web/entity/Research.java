package com.web.entity;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.web.enums.ResearchStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "research")
public class Research extends BaseEntity {

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(name = "abstract", columnDefinition = "TEXT")
    private String abstractText;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String authors;

    private String institution;

    @Column(name = "published_year")
    private Integer publishedYear;

    private String journal;

    private String field;

    private String imageBanner;

    private String linkDocument;

    private ResearchStatus researchStatus;

    private Integer views = 0;

    @OneToMany(mappedBy = "research", cascade = CascadeType.REMOVE)
    @JsonManagedReference
    private List<ResearchPlant> researchPlants = new ArrayList<>();

    @OneToMany(mappedBy = "research", cascade = CascadeType.REMOVE)
    @JsonManagedReference
    private List<ResearchExpert> researchExperts = new ArrayList<>();

    @JsonProperty("color")
    private String getColor(){
        return this.researchStatus.getColor();
    }

    @JsonProperty("statusLabel")
    private String getLabelStatus(){
        return this.researchStatus.getLabel();
    }
}
