package com.web.controller.user;

import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

@Controller
public class CustomErrorController implements ErrorController {

    @RequestMapping("/error")
    public void handleError(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int statusCode = (status != null) ? Integer.parseInt(status.toString()) : 500;

        // Nếu response đã được commit (ví dụ: đang xuất file Excel/CSV), không ghi đè nữa
        if (response.isCommitted()) {
            return;
        }

        response.setContentType("text/html; charset=UTF-8");
        response.setStatus(statusCode);
        PrintWriter w = response.getWriter();
        w.println("<!DOCTYPE html><html lang='vi'><head><meta charset='UTF-8'><title>Lỗi " + statusCode + " - DuocLieuVN</title>");
        w.println("<link href='https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.min.css' rel='stylesheet'>");
        w.println("<style>body{background:#f8f9fa;font-family:Arial;text-align:center;padding:80px 20px}");
        w.println("h1{font-size:100px;font-weight:700;color:#dc3545;margin:0}h2{color:#333}p{color:#666}");
        w.println("a{display:inline-block;padding:12px 30px;background:#dc3545;color:#fff;text-decoration:none;border-radius:30px;margin-top:20px}</style>");
        w.println("</head><body><div style='max-width:600px;margin:0 auto'>");
        w.println("<h1>" + statusCode + "</h1>");
        w.println("<h2>" + getMessage(statusCode) + "</h2>");
        w.println("<p>Vui lòng thử lại hoặc quay về trang chủ.</p>");
        w.println("<a href='/'>Quay về Trang Chủ</a>");
        w.println("</div></body></html>");
    }

    private String getMessage(int code) {
        switch (code) {
            case 400: return "Yêu cầu không hợp lệ";
            case 403: return "Bạn không có quyền truy cập";
            case 404: return "Không tìm thấy trang";
            case 417: return "Dữ liệu không hợp lệ";
            case 500: default: return "Đã xảy ra lỗi máy chủ";
        }
    }
}
