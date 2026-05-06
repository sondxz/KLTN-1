package com.web.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.web.utils.SlugGenerator;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "folk_remedies")
public class FolkRemedy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "usage_instruction", columnDefinition = "TEXT")
    private String usageInstruction;

    @Column(columnDefinition = "TEXT")
    private String preparation;

    @Column(columnDefinition = "TEXT")
    private String contraindication;

    @Column(length = 500)
    private String source;

    @Column(nullable = false)
    private String status = "pending"; // pending, approved, rejected

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", updatable = false)
    @JsonFormat(pattern = "HH:mm dd/MM/yyyy")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    @JsonFormat(pattern = "HH:mm dd/MM/yyyy")
    private LocalDateTime updatedAt;

    /**
     * Quan hệ N-N với Plant
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "folk_remedy_plants",
            joinColumns = @JoinColumn(name = "folk_remedy_id"),
            inverseJoinColumns = @JoinColumn(name = "plant_id")
    )
    @JsonIgnoreProperties({"plantMedia", "plantDiseases", "families"})
    private List<Plant> plants = new ArrayList<>();

    /**
     * Quan hệ N-N với Diseases
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "folk_remedy_diseases",
            joinColumns = @JoinColumn(name = "folk_remedy_id"),
            inverseJoinColumns = @JoinColumn(name = "disease_id")
    )
    private List<Diseases> diseases = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (slug == null || slug.isEmpty()) {
            slug = SlugGenerator.generateSlug(name);
        }
        if (status == null) {
            status = "pending";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
