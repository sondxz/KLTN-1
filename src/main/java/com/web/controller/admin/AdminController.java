package com.web.controller.admin;

import com.web.utils.Contains;
import com.web.utils.UserUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private UserUtils userUtils;

    @RequestMapping(value = {"/create-article"}, method = RequestMethod.GET)
    public String createArticle() {
        return "admin/create-article.html";
    }

    @RequestMapping(value = {"/create-plant"}, method = RequestMethod.GET)
    public String createPlant() {
        return "admin/create-plant.html";
    }

    @RequestMapping(value = {"/create-folk-remedy"}, method = RequestMethod.GET)
    public String createFolkRemedy() {
        return "admin/create-folk-remedy.html";
    }

    @RequestMapping(value = {"/create-user"}, method = RequestMethod.GET)
    public String createUser() {
        return "admin/create-user.html";
    }

    @RequestMapping(value = {"/create-research"}, method = RequestMethod.GET)
    public String createResearch() {
        return "admin/create-research.html";
    }

    @RequestMapping(value = {"/create-expert"}, method = RequestMethod.GET)
    public String createExpert() {
        return "admin/create-expert.html";
    }

    @RequestMapping(value = {"/index"}, method = RequestMethod.GET)
    public String index() {
        return "redirect:/admin/list-plant";
    }

    @RequestMapping(value = {"/expert-index"}, method = RequestMethod.GET)
    public String expertIndex() {
        return "redirect:/admin/pending-approval";
    }

    @RequestMapping(value = {"/list-article"}, method = RequestMethod.GET)
    public String listArticle() {
        return "admin/list-article.html";
    }

    @RequestMapping(value = {"/list-diseases"}, method = RequestMethod.GET)
    public String listDiseases() {
        return "admin/list-diseases.html";
    }

    @RequestMapping(value = {"/list-folk-remedies"}, method = RequestMethod.GET)
    public String listFolkRemedies() {
        return "admin/list-folk-remedies.html";
    }

    @RequestMapping(value = {"/list-families"}, method = RequestMethod.GET)
    public String listFamilies() {
        return "admin/list-families.html";
    }

    @RequestMapping(value = {"/list-plant"}, method = RequestMethod.GET)
    public String listPlant() {
        return "admin/list-plant.html";
    }

    @RequestMapping(value = {"/list-user"}, method = RequestMethod.GET)
    public String listUser() {
        return "admin/list-user.html";
    }

    @RequestMapping(value = {"/list-research"}, method = RequestMethod.GET)
    public String listResearch() {
        return "admin/list-research.html";
    }

    @RequestMapping(value = {"/list-expert"}, method = RequestMethod.GET)
    public String listExpert() {
        return "admin/list-expert.html";
    }

    @RequestMapping(value = {"/list-comment"}, method = RequestMethod.GET)
    public String listComment() {
        return "admin/list-comment.html";
    }

    @RequestMapping(value = {"/pending-approval"}, method = RequestMethod.GET)
    public String pendingApproval() {
        return "admin/pending-approval.html";
    }

    @RequestMapping(value = {"/expert-messages"}, method = RequestMethod.GET)
    public String expertMessages() {
        return "admin/expert-messages.html";
    }

    @RequestMapping(value = {"/statistics"}, method = RequestMethod.GET)
    public String statistics() {
        return "admin/dashboard.html";
    }

}
