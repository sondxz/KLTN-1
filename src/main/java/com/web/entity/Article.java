package com.web.entity;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.web.enums.ArticleStatus;
import com.web.utils.SlugGenerator;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "articles")
public class Article extends BaseEntity {

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String excerpt;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    private String imageBanner;

    @Enumerated(EnumType.STRING)
    private ArticleStatus articleStatus;

    @Column(name = "is_featured")
    private Boolean isFeatured = false;

    @Column(name = "allow_comments")
    private Boolean allowComments = true;

    @Column(name = "view_count", nullable = false)
    private Long viewCount = 0L;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @ManyToOne
    @JsonIgnoreProperties({"expert", "password", "activation_key", "rememberKey"})
    private User user;

    @ManyToOne
    @JsonIgnoreProperties({"articles"})
    private Diseases diseases;

    @JsonProperty("color")
    private String getColor(){
        return this.articleStatus.getColor();
    }

    @JsonProperty("statusLabel")
    private String getLabelStatus(){
        return this.articleStatus.getLabel();
    }
}
