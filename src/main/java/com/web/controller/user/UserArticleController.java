package com.web.controller.user;

import com.web.entity.Article;
import com.web.service.ArticleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.server.ResponseStatusException;

import javax.servlet.http.HttpSession;

@Controller
public class UserArticleController {

    @Autowired
    private ArticleService articleService;

    @RequestMapping(value = {"/article-detail/{slug}"}, method = RequestMethod.GET)
    public String articleDetail(Model model, @PathVariable String slug, HttpSession session) {
        // Tìm bài viết public trước, fallback tìm tất cả (cho admin xem bài CHỜ DUYỆT)
        Article article = articleService.findBySlugPublic(slug);
        if(article == null){
            article = articleService.findBySlug(slug);
        }
        if(article == null){
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "article not found");
        }
        
        // Tăng view count với chống spam (bắt exception để không làm gián đoạn response)
        try {
            articleService.incrementViewCount(article.getId(), session);
            // Reload article để lấy viewCount mới nhất
            article = articleService.findBySlug(slug);
        } catch (Exception e) {
            // Log lỗi nhưng không làm gián đoạn việc hiển thị bài viết
            // Logger sẽ được log trong ArticleService
            // Giữ nguyên article đã load
        }
        
        model.addAttribute("article", article);
        return "user/article-detail.html";
    }
}
