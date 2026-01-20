package com.web.controller;

import com.web.entity.Message;
import com.web.entity.User;
import com.web.repository.MessageRepository;
import com.web.repository.UserRepository;
import com.web.service.MessageService;
import com.web.service.RateLimitService;
import com.web.utils.MessageValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Controller
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private MessageService messageService;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MessageValidator messageValidator;

    @Autowired
    private RateLimitService rateLimitService;

    /**
     * Xử lý tin nhắn từ client
     * Client gửi đến: /app/chat.sendMessage
     * Server gửi đến: /topic/chat/{receiverId}
     */
    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload Map<String, Object> payload, Principal principal) {
        Long senderId = null;
        try {
            // Validate principal
            if (principal == null || principal.getName() == null) {
                throw new RuntimeException("Người dùng chưa đăng nhập");
            }

            senderId = Long.parseLong(principal.getName());

            // Rate limiting
            if (rateLimitService.isRateLimited(senderId)) {
                throw new RuntimeException("Bạn đã gửi quá nhiều tin nhắn. Vui lòng đợi một chút.");
            }

            // Validate và parse input
            if (payload.get("receiverId") == null) {
                throw new IllegalArgumentException("Người nhận không được để trống");
            }

            Long receiverId = Long.parseLong(payload.get("receiverId").toString());
            String content = (String) payload.get("content");
            String messageType = messageValidator.validateMessageType(
                payload.get("messageType") != null ? payload.get("messageType").toString() : "text"
            );
            String fileUrl = payload.get("fileUrl") != null ? 
                messageValidator.validateFileUrl(payload.get("fileUrl").toString()) : null;
            String fileName = payload.get("fileName") != null ? 
                messageValidator.validateFileName(payload.get("fileName").toString()) : null;
            String fileType = payload.get("fileType") != null ? payload.get("fileType").toString() : null;

            // Validate content hoặc fileUrl phải có ít nhất 1
            if ((content == null || content.trim().isEmpty()) && fileUrl == null) {
                throw new IllegalArgumentException("Tin nhắn hoặc file không được để trống");
            }

            // Sanitize content
            if (content != null && !content.trim().isEmpty()) {
                content = messageValidator.validateAndSanitize(content);
            }

            // Lấy sender và receiver
            User sender = userRepository.findById(senderId)
                    .orElseThrow(() -> new RuntimeException("Người gửi không tồn tại"));
            User receiver = userRepository.findById(receiverId)
                    .orElseThrow(() -> new RuntimeException("Người nhận không tồn tại"));

            // Kiểm tra quyền: User chỉ chat với Expert và ngược lại
            String senderRole = sender.getAuthorities() != null ? sender.getAuthorities().getName() : null;
            String receiverRole = receiver.getAuthorities() != null ? receiver.getAuthorities().getName() : null;

            boolean isValidChat = false;
            if ("ROLE_USER".equals(senderRole) && "ROLE_EXPERT".equals(receiverRole)) {
                isValidChat = true;
            } else if ("ROLE_EXPERT".equals(senderRole) && "ROLE_USER".equals(receiverRole)) {
                isValidChat = true;
            }

            if (!isValidChat) {
                // Ném IllegalArgumentException để gửi thông báo cụ thể về client
                throw new IllegalArgumentException("Chỉ User và Expert mới có thể chat với nhau");
            }

            // Tạo và lưu message
            Message message = new Message();
            message.setSender(sender);
            message.setReceiver(receiver);
            message.setContent(content);
            message.setMessageType(messageType);
            message.setFileUrl(fileUrl);
            message.setFileName(fileName);
            message.setFileType(fileType);
            message.setIsRead(false);
            message.setCreatedAt(LocalDateTime.now());

            Message savedMessage = messageRepository.save(message);

            // Chuẩn bị response
            Map<String, Object> response = new HashMap<>();
            response.put("id", savedMessage.getId());
            response.put("senderId", sender.getId());
            response.put("senderName", sender.getFullname() != null ? sender.getFullname() : sender.getUsername());
            response.put("receiverId", receiver.getId());
            response.put("receiverName", receiver.getFullname() != null ? receiver.getFullname() : receiver.getUsername());
            response.put("content", savedMessage.getContent());
            response.put("messageType", savedMessage.getMessageType());
            response.put("fileUrl", savedMessage.getFileUrl());
            response.put("fileName", savedMessage.getFileName());
            response.put("fileType", savedMessage.getFileType());
            response.put("isRead", savedMessage.getIsRead());
            response.put("createdAt", savedMessage.getCreatedAt());

            // Gửi tin nhắn đến người nhận
            messagingTemplate.convertAndSend("/topic/chat/" + receiverId, response);
            
            // Gửi lại cho người gửi để confirm
            messagingTemplate.convertAndSend("/topic/chat/" + senderId, response);

            logger.info("Message sent from user {} to user {}", senderId, receiverId);

        } catch (IllegalArgumentException e) {
            // Validation error - gửi lỗi về cho người gửi
            logger.warn("Validation error for user {}: {}", senderId, e.getMessage());
            sendErrorResponse(senderId, e.getMessage());
        } catch (Exception e) {
            // Unexpected error - log và gửi lỗi generic
            logger.error("Error sending message from user {}: {}", senderId, e.getMessage(), e);
            // Ưu tiên dùng thông điệp cụ thể nếu có (ví dụ: "Chỉ User và Expert mới có thể chat với nhau")
            String errorMessage = (e.getMessage() != null && !e.getMessage().isEmpty())
                    ? e.getMessage()
                    : "Có lỗi xảy ra khi gửi tin nhắn. Vui lòng thử lại.";
            sendErrorResponse(senderId, errorMessage);
        }
    }

    private void sendErrorResponse(Long userId, String errorMessage) {
        if (userId != null) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", errorMessage);
            messagingTemplate.convertAndSend("/topic/chat/" + userId, errorResponse);
        }
    }

    /**
     * Đánh dấu tin nhắn đã đọc
     */
    @MessageMapping("/chat.markRead")
    public void markAsRead(@Payload Map<String, Object> payload, Principal principal) {
        try {
            if (principal == null || principal.getName() == null) {
                logger.warn("Unauthorized mark as read attempt");
                return;
            }

            if (payload.get("messageId") == null) {
                logger.warn("MessageId is null in markAsRead");
                return;
            }

            Long messageId = Long.parseLong(payload.get("messageId").toString());
            Long currentUserId = Long.parseLong(principal.getName());

            Message message = messageRepository.findById(messageId)
                    .orElseThrow(() -> new RuntimeException("Tin nhắn không tồn tại"));

            // Chỉ người nhận mới đánh dấu đã đọc
            if (message.getReceiver().getId().equals(currentUserId)) {
                message.setIsRead(true);
                messageRepository.save(message);

                // Thông báo cho người gửi biết tin nhắn đã được đọc
                Map<String, Object> response = new HashMap<>();
                response.put("messageId", messageId);
                response.put("isRead", true);
                messagingTemplate.convertAndSend("/topic/chat/" + message.getSender().getId(), response);
            } else {
                logger.warn("User {} attempted to mark message {} as read but is not the receiver", 
                    currentUserId, messageId);
            }
        } catch (NumberFormatException e) {
            logger.warn("Invalid messageId format in markAsRead: {}", payload.get("messageId"));
        } catch (Exception e) {
            logger.error("Error marking message as read: {}", e.getMessage(), e);
        }
    }
}

