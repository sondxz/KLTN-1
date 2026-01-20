package com.web.controller.user;

import com.web.entity.Article;
import com.web.entity.Research;
import com.web.service.ArticleService;
import com.web.service.ResearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class UserResearchController {

    @Autowired
    private ResearchService researchService;

    @RequestMapping(value = {"/research-detail/{slug}"}, method = RequestMethod.GET)
    public String articleDetail(Model model, @PathVariable String slug) {
        // Chỉ cho phép truy cập các nghiên cứu đã xuất bản (public)
        Research research = researchService.findBySlugPublic(slug);
        if(research == null){
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Research not found");
        }
        model.addAttribute("research", research);
        return "user/research-detail.html";
    }
}
