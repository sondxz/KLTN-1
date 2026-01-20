package com.web.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import javax.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "comments")
public class Comment extends BaseEntity {
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @ManyToOne
    @JoinColumn(name = "user_id")
    @JsonIgnoreProperties({"expert", "password", "activation_key", "rememberKey"})
    private User user;

    @ManyToOne
    @JoinColumn(name = "article_id")
    @JsonIgnoreProperties({"comments", "tags", "author"})
    private Article article;

    @ManyToOne
    @JoinColumn(name = "parent_id")
    @JsonIgnoreProperties({"parent", "replies"})
    private Comment parent;

    @OneToMany(mappedBy = "parent")
    @JsonIgnoreProperties({"parent", "replies"})
    private List<Comment> replies;

    @Column(nullable = false)
    private Integer status = 1; // Default: pending
}
