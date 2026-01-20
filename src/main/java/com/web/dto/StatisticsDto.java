package com.web.dto;

import com.web.entity.Article;
import com.web.entity.Plant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StatisticsDto {
    // Tổng số
    private Long totalPlants;
    private Long totalUsers;
    private Long totalExperts;
    private Long totalArticles;
    private Long totalResearch;
    private Long totalComments;
    private Long totalFamilies;
    private Long totalDiseases;
    
    // Trạng thái
    private Long pendingPlants;
    private Long pendingArticles;
    private Long activeUsers;
    private Long lockedUsers;
    
    // Thống kê tháng này
    private Long newPlantsThisMonth;
    private Long newUsersThisMonth;
    private Long newArticlesThisMonth;
    private Long newResearchThisMonth;
    
    // Top items
    private List<Plant> topPlants;
    private List<Article> topArticles;
}

