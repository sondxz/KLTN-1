package com.web.utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;

import javax.mail.*;
import javax.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;

@Service
@EnableAsync
public class MailService {

    private final Logger log = LoggerFactory.getLogger(MailService.class);
    private final JavaMailSender javaMailSender;

    public MailService(JavaMailSender javaMailSender) {
        this.javaMailSender = javaMailSender;
    }


    @Value("${spring.mail.username}")
    private String username;

    @Async
    public void sendEmail(String to, String subject, String content, boolean isMultipart, boolean isHtml) {
        log.info(
                "📧 Attempting to send email[multipart '{}' and html '{}'] to '{}' with subject '{}'",
                isMultipart,
                isHtml,
                to,
                subject
        );

        // Prepare message using a Spring helper
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        try {
            MimeMessageHelper message = new MimeMessageHelper(mimeMessage, isMultipart, StandardCharsets.UTF_8.name());
            message.setTo(to);
            // Sử dụng display name để email trông chuyên nghiệp hơn
            message.setFrom(username, "DuocLieuVN - Hệ thống Quản lý Cây Dược Liệu");
            message.setSubject(subject);
            message.setText(content, isHtml);
            
            // Thêm headers để tránh spam filter
            mimeMessage.setHeader("X-Mailer", "DuocLieuVN Mail Service");
            mimeMessage.setHeader("X-Priority", "1");
            
            javaMailSender.send(mimeMessage);
            log.info("Email sent successfully to: {}", to);
        } catch (MailException | MessagingException e) {
            log.error("Email could not be sent to user '{}'", to, e);
        } catch (Exception e) {
            log.error("Unexpected error sending email to '{}'", to, e);
        }
    }
}
