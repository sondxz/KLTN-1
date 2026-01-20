package com.web.utils;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * Utility class để validate và sanitize message content
 */
@Component
public class MessageValidator {

    private static final int MAX_CONTENT_LENGTH = 5000;
    private static final int MAX_FILE_NAME_LENGTH = 255;
    private static final Pattern SCRIPT_PATTERN = Pattern.compile(
        "<script[^>]*>.*?</script>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern JAVASCRIPT_PATTERN = Pattern.compile(
        "javascript:", Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ON_EVENT_PATTERN = Pattern.compile(
        "on\\w+\\s*=", Pattern.CASE_INSENSITIVE
    );

    /**
     * Validate và sanitize message content
     */
    public String validateAndSanitize(String content) {
        if (content == null) {
            return null;
        }

        // Trim whitespace
        content = content.trim();

        // Kiểm tra độ dài
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("Tin nhắn không được vượt quá " + MAX_CONTENT_LENGTH + " ký tự");
        }

        // Escape HTML để tránh XSS
        content = escapeHtml(content);

        // Loại bỏ các pattern nguy hiểm
        content = SCRIPT_PATTERN.matcher(content).replaceAll("");
        content = JAVASCRIPT_PATTERN.matcher(content).replaceAll("");
        content = ON_EVENT_PATTERN.matcher(content).replaceAll("");

        return content;
    }

    /**
     * Validate file name
     */
    public String validateFileName(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return null;
        }

        fileName = fileName.trim();

        if (fileName.length() > MAX_FILE_NAME_LENGTH) {
            throw new IllegalArgumentException("Tên file không được vượt quá " + MAX_FILE_NAME_LENGTH + " ký tự");
        }

        // Loại bỏ các ký tự nguy hiểm
        fileName = fileName.replaceAll("[<>:\"|?*\\\\]", "");

        return fileName;
    }

    /**
     * Validate message type
     */
    public String validateMessageType(String messageType) {
        if (!StringUtils.hasText(messageType)) {
            return "text";
        }

        messageType = messageType.toLowerCase().trim();
        
        // Chỉ cho phép các type hợp lệ
        if (!messageType.equals("text") && 
            !messageType.equals("image") && 
            !messageType.equals("file") &&
            !messageType.equals("video")) {
            return "text";
        }

        return messageType;
    }

    /**
     * Validate file URL (chỉ cho phép HTTP/HTTPS)
     */
    public String validateFileUrl(String fileUrl) {
        if (!StringUtils.hasText(fileUrl)) {
            return null;
        }

        fileUrl = fileUrl.trim();

        // Chỉ cho phép HTTP/HTTPS URLs
        if (!fileUrl.startsWith("http://") && !fileUrl.startsWith("https://")) {
            throw new IllegalArgumentException("File URL không hợp lệ");
        }

        // Kiểm tra độ dài URL
        if (fileUrl.length() > 500) {
            throw new IllegalArgumentException("File URL quá dài");
        }

        return fileUrl;
    }

    /**
     * Escape HTML để tránh XSS
     */
    private String escapeHtml(String input) {
        if (input == null) {
            return null;
        }

        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;")
            .replace("/", "&#x2F;");
    }
}



