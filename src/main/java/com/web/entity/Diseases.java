package com.web.entity;

import javax.persistence.*;

import com.web.utils.SlugGenerator;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "diseases")
public class Diseases extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    private String description;

    public void setSlug(String slug) {
        if (slug == null || slug.isEmpty()) {
            this.slug = SlugGenerator.generateSlug(this.name);
        }
    }
}
