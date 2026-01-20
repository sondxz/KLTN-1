package com.web.controller.user;

import com.web.entity.Article;
import com.web.entity.Plant;
import com.web.service.ArticleService;
import com.web.service.PlantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.List;

@Controller
public class UserController {

    @Autowired
    private PlantService plantService;

    @Autowired
    private ArticleService articleService;

    @RequestMapping(value = {"/about"}, method = RequestMethod.GET)
    public String about(Model model) {
        return "user/about.html";
    }

    @RequestMapping(value = {"/articles"}, method = RequestMethod.GET)
    public String articles(Model model) {
        // Lấy top 6 bài viết được xem nhiều nhất để hiển thị tag "Nổi bật"
        List<Article> topViewedArticles = articleService.getTopViewedArticles(6);
        model.addAttribute("topViewedArticles", topViewedArticles);
        return "user/articles.html";
    }

    @RequestMapping(value = {"/confirm"}, method = RequestMethod.GET)
    public String confirm(Model model) {
        return "user/confirm.html";
    }

    @RequestMapping(value = {"/experts"}, method = RequestMethod.GET)
    public String experts(Model model) {
        return "user/experts.html";
    }

    @RequestMapping(value = {"/forgot"}, method = RequestMethod.GET)
    public String forgot(Model model) {
        return "user/forgot.html";
    }

    @RequestMapping(value = {"/","/index"}, method = RequestMethod.GET)
    public String index(Model model) {
        // Lấy top 6 cây dược liệu được xem nhiều nhất
        List<Plant> topViewedPlants = plantService.getTopViewedPlants(6);
        model.addAttribute("topViewedPlants", topViewedPlants);
        
        // Lấy top 6 bài viết được xem nhiều nhất
        List<Article> topViewedArticles = articleService.getTopViewedArticles(6);
        model.addAttribute("topViewedArticles", topViewedArticles);
        
        return "user/index.html";
    }

    @RequestMapping(value = {"/login"}, method = RequestMethod.GET)
    public String login(Model model) {
        return "user/login.html";
    }

    @RequestMapping(value = {"/reset-password", "/reset-password.html"}, method = RequestMethod.GET)
    public String resetpassword(Model model) {
        return "user/reset-password.html";
    }

    @RequestMapping(value = {"/my-account"}, method = RequestMethod.GET)
    public String myAccount(Model model) {
        return "user/my-account.html";
    }

    @RequestMapping(value = {"/plant"}, method = RequestMethod.GET)
    public String plant(Model model) {
        return "user/plant.html";
    }

    @RequestMapping(value = {"/regis"}, method = RequestMethod.GET)
    public String regis(Model model) {
        return "user/regis.html";
    }

    @RequestMapping(value = {"/research"}, method = RequestMethod.GET)
    public String research(Model model) {
        return "user/research.html";
    }

    @RequestMapping(value = {"/create-plant"}, method = RequestMethod.GET)
    public String createPlant(Model model) {
        return "user/create-plant.html";
    }

    @RequestMapping(value = {"/create-article"}, method = RequestMethod.GET)
    public String createArticle(Model model) {
        return "user/create-article.html";
    }

    @RequestMapping(value = {"/user/messages"}, method = RequestMethod.GET)
    public String userMessages(Model model) {
        return "user/messages.html";
    }

}
