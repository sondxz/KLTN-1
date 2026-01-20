package com.web.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import javax.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "experts")
public class Expert extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    private String title;

    private String email;

    private String phone;

    private String specialization;

    private String institution;

    private String avatar;

    private Integer status;

    @Column(columnDefinition = "TEXT")
    private String education;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Column(columnDefinition = "TEXT")
    private String achievements;

    @Column(name = "contact_email")
    private String contactEmail;

    @OneToOne
    @JoinColumn(name = "user_id")
    @JsonIgnoreProperties({"expert", "password", "activation_key", "rememberKey"})
    private User user;

}
