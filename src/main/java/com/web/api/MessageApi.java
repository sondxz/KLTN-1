package com.web.api;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.web.entity.Message;
import com.web.entity.User;
import com.web.service.MessageService;
import com.web.utils.UserUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/message")
public class MessageApi {

    @Autowired
    private MessageService messageService;

    @Autowired
    private UserUtils userUtils;

    @Autowired
    private Cloudinary cloudinary;

    @org.springframework.beans.factory.annotation.Value("${websocket.file.max-size:10485760}")
    private long maxFileSize; // Default 10MB

    // User gửi tin nhắn cho Expert
    @PostMapping("/user/send")
    public ResponseEntity<Message> sendMessage(@RequestBody Map<String, Object> request) {
        Long expertUserId = Long.valueOf(request.get("expertUserId").toString());
        String content = (String) request.get("content");
        String messageType = request.get("messageType") != null ? request.get("messageType").toString() : "text";
        String fileUrl = request.get("fileUrl") != null ? request.get("fileUrl").toString() : null;
        String fileName = request.get("fileName") != null ? request.get("fileName").toString() : null;
        String fileType = request.get("fileType") != null ? request.get("fileType").toString() : null;
        return ResponseEntity.ok(messageService.sendMessage(expertUserId, content, messageType, fileUrl, fileName, fileType));
    }

    // Expert trả lời tin nhắn
    @PostMapping("/expert/reply")
    public ResponseEntity<Message> replyMessage(@RequestBody Map<String, Object> request) {
        Long messageId = Long.valueOf(request.get("messageId").toString());
        String content = (String) request.get("content");
        String messageType = request.get("messageType") != null ? request.get("messageType").toString() : "text";
        String fileUrl = request.get("fileUrl") != null ? request.get("fileUrl").toString() : null;
        String fileName = request.get("fileName") != null ? request.get("fileName").toString() : null;
        String fileType = request.get("fileType") != null ? request.get("fileType").toString() : null;
        return ResponseEntity.ok(messageService.replyMessage(messageId, content, messageType, fileUrl, fileName, fileType));
    }

    // Lấy conversation giữa user và expert
    @GetMapping("/user/conversation")
    public ResponseEntity<List<Message>> getConversation(@RequestParam Long expertUserId) {
        return ResponseEntity.ok(messageService.getConversation(expertUserId));
    }

    // Expert xem danh sách tin nhắn đến
    @GetMapping("/expert/inbox")
    public ResponseEntity<Page<Message>> getInboxMessages(Pageable pageable) {
        return ResponseEntity.ok(messageService.getInboxMessages(pageable));
    }

    // User xem danh sách tin nhắn đã gửi
    @GetMapping("/user/sent")
    public ResponseEntity<Page<Message>> getSentMessages(Pageable pageable) {
        return ResponseEntity.ok(messageService.getSentMessages(pageable));
    }

    // Đếm số tin nhắn chưa đọc (cho expert)
    @GetMapping("/expert/unread-count")
    public ResponseEntity<Long> getUnreadCount() {
        return ResponseEntity.ok(messageService.countUnreadMessages());
    }

    // Lấy danh sách người đã nhắn tin với expert
    @GetMapping("/expert/conversation-partners")
    public ResponseEntity<List<User>> getConversationPartners() {
        return ResponseEntity.ok(messageService.getConversationPartners());
    }

    // Lấy danh sách experts mà user đã chat
    @GetMapping("/user/conversation-partners")
    public ResponseEntity<List<User>> getUserConversationPartners() {
        return ResponseEntity.ok(messageService.getUserConversationPartners());
    }

    // Đếm số tin nhắn chưa đọc (cho user)
    @GetMapping("/user/unread-count")
    public ResponseEntity<Long> getUserUnreadCount() {
        return ResponseEntity.ok(messageService.countUserUnreadMessages());
    }

    // Expert lấy conversation với user cụ thể
    @GetMapping("/expert/conversation")
    public ResponseEntity<List<Message>> getExpertConversation(@RequestParam Long userId) {
        return ResponseEntity.ok(messageService.getExpertConversation(userId));
    }

    // Đánh dấu tin nhắn đã đọc
    @PostMapping("/mark-read")
    public ResponseEntity<String> markAsRead(@RequestParam Long messageId) {
        messageService.markAsRead(messageId);
        return ResponseEntity.ok("Đã đánh dấu đã đọc");
    }

    /**
     * Upload file/hình ảnh cho chat
     */
    @PostMapping("/upload-file")
    public ResponseEntity<Map<String, String>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("receiverId") Long receiverId) {
        try {
            Map<String, String> result = new HashMap<>();
            
            if (file.isEmpty()) {
                result.put("error", "File không được để trống");
                return ResponseEntity.badRequest().body(result);
            }

            // Validate file size
            if (file.getSize() > maxFileSize) {
                result.put("error", "File không được vượt quá " + (maxFileSize / 1024 / 1024) + "MB");
                return ResponseEntity.badRequest().body(result);
            }

            // Upload lên Cloudinary
            Map uploadResult = cloudinary.uploader().upload(file.getBytes(), 
                ObjectUtils.asMap(
                    "folder", "chat-files",
                    "resource_type", "auto"
                ));

            String fileUrl = (String) uploadResult.get("secure_url");
            String fileName = file.getOriginalFilename();
            String resourceType = (String) uploadResult.get("resource_type");
            
            // Xác định file type
            String fileType = "file";
            if ("image".equals(resourceType)) {
                fileType = "image";
            } else if ("video".equals(resourceType)) {
                fileType = "video";
            } else if ("raw".equals(resourceType)) {
                fileType = "document";
            }

            result.put("fileUrl", fileUrl);
            result.put("fileName", fileName);
            result.put("fileType", fileType);
            result.put("message", "Upload thành công");

            return ResponseEntity.ok(result);
        } catch (IOException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Lỗi khi upload file: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }
}

