package com.web.service;

import com.web.dto.StatisticsDto;
import com.web.entity.Article;
import com.web.entity.Plant;
import com.web.enums.ArticleStatus;
import com.web.enums.PlantStatus;
import com.web.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.YearMonth;

@Service
public class StatisticsService {

    @Autowired
    private PlantRepository plantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ExpertRepository expertRepository;

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private ResearchRepository researchRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private FamiliesRepository familiesRepository;

    @Autowired
    private DiseasesRepository diseasesRepository;

    @Autowired
    private FolkRemedyRepository folkRemedyRepository;

    /**
     * Lấy thống kê tổng quan - tối ưu bằng cách dùng COUNT queries
     */
    public StatisticsDto getStatistics() {
        StatisticsDto stats = new StatisticsDto();

        // Lấy thời điểm đầu tháng hiện tại
        LocalDateTime startOfMonth = YearMonth.now().atDay(1).atStartOfDay();

        // Tổng số - dùng count() nhanh hơn load all
        stats.setTotalPlants(plantRepository.count());
        stats.setTotalUsers(userRepository.count());
        stats.setTotalExperts(expertRepository.count());
        stats.setTotalArticles(articleRepository.count());
        stats.setTotalResearch(researchRepository.count());
        stats.setTotalComments(commentRepository.count());
        stats.setTotalFamilies(familiesRepository.count());
        stats.setTotalGenera(plantRepository.countDistinctGenus());
        stats.setTotalDiseases(diseasesRepository.count());
        stats.setTotalFolkRemedies(folkRemedyRepository.count());

        // Trạng thái - dùng countBy queries
        stats.setPendingPlants(plantRepository.countByPlantStatus(PlantStatus.CHO_DUYET));
        stats.setPendingArticles(articleRepository.countByStatus(ArticleStatus.CHO_DUYET));
        stats.setPendingFolkRemedies(folkRemedyRepository.countByStatus("pending"));
        stats.setActiveUsers(userRepository.countByActived(true));
        stats.setLockedUsers(userRepository.countByActived(false));

        // Thống kê tháng này - dùng countBy queries tối ưu
        stats.setNewPlantsThisMonth(plantRepository.countByCreatedAtAfter(startOfMonth));
        stats.setNewUsersThisMonth(userRepository.countByCreatedAtAfter(startOfMonth));
        stats.setNewArticlesThisMonth(articleRepository.countByCreatedAtAfter(startOfMonth));
        stats.setNewResearchThisMonth(researchRepository.countByCreatedAtAfter(startOfMonth));

        // Top items - chỉ lấy top 5 để tối ưu
        Pageable topLimit = PageRequest.of(0, 5);
        stats.setTopPlants(plantRepository.findTopViewed(PlantStatus.DA_XUAT_BAN, topLimit));
        stats.setTopArticles(articleRepository.findTopViewed(ArticleStatus.DA_XUAT_BAN, topLimit));

        return stats;
    }
}

