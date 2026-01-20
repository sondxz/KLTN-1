package com.web.dto;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExpertDto {
    private Long id;

    @NotBlank(message = "Tên chuyên gia không được để trống")
    private String name;

    private String slug;
    private String title;
    private String specialization;
    private String institution;
    private String education;
    private String bio;
    private String achievements;

    @Email(message = "Email không hợp lệ")
    private String contactEmail;

    private Integer avatarId;
    private MediaDto avatar;
    private Integer status;
    private String createdAt;
    private String updatedAt;
//    private List<PlantDto> plants;
}
