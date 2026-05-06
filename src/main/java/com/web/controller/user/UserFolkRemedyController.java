package com.web.controller.user;

import com.web.entity.FolkRemedy;
import com.web.service.FolkRemedyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class UserFolkRemedyController {

    @Autowired
    private FolkRemedyService folkRemedyService;

    /**
     * Danh sách bài thuốc dân gian (public)
     */
    @RequestMapping(value = {"/folk-remedies"}, method = RequestMethod.GET)
    public String folkRemediesList() {
        return "user/folk-remedies.html";
    }

    /**
     * Chi tiết bài thuốc dân gian (public)
     */
    @RequestMapping(value = {"/folk-remedies/{id}"}, method = RequestMethod.GET)
    public String folkRemedyDetail(Model model, @PathVariable Long id) {
        try {
            FolkRemedy folkRemedy = folkRemedyService.findByIdPublic(id);
            model.addAttribute("folkRemedy", folkRemedy);
            return "user/folk-remedy-detail.html";
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bài thuốc");
        }
    }
}
