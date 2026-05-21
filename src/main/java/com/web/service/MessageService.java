package com.web.service;

import com.web.entity.Message;
import com.web.entity.User;
import com.web.exception.MessageException;
import com.web.repository.MessageRepository;
import com.web.repository.UserRepository;
import com.web.utils.UserUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class MessageService {

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserUtils userUtils;

    @Autowired
    private NotificationService notificationService;

    // User gửi tin nhắn cho Expert
    @Transactional
    public Message sendMessage(Long expertUserId, String content, String messageType, String fileUrl, String fileName, String fileType) {
        User currentUser = userUtils.getUserWithAuthority();
        if (currentUser == null) {
            throw new MessageException("Người dùng chưa đăng nhập");
        }

        // Kiểm tra expert có tồn tại và có role EXPERT không
        User expert = userRepository.findById(expertUserId)
                .orElseThrow(() -> new MessageException("Chuyên gia không tồn tại"));

        if (expert.getAuthorities() == null || !expert.getAuthorities().getName().equals("ROLE_EXPERT")) {
            throw new MessageException("Người dùng này không phải là chuyên gia");
        }

        // Tạo tin nhắn
        Message message = new Message();
        message.setSender(currentUser);
        message.setReceiver(expert);
        message.setContent(content);
        message.setMessageType(messageType != null ? messageType : "text");
        message.setFileUrl(fileUrl);
        message.setFileName(fileName);
        message.setFileType(fileType);
        message.setIsRead(false);

        Message savedMessage = messageRepository.save(message);

        // Gửi email thông báo cho Expert (async, không block)
        notificationService.notifyNewMessage(savedMessage);

        return savedMessage;
    }

    // Expert trả lời tin nhắn
    @Transactional
    public Message replyMessage(Long messageId, String content, String messageType, String fileUrl, String fileName, String fileType) {
        User currentUser = userUtils.getUserWithAuthority();
        if (currentUser == null) {
            throw new MessageException("Chuyên gia chưa đăng nhập");
        }

        // Kiểm tra current user có phải expert không
        if (currentUser.getAuthorities() == null || !currentUser.getAuthorities().getName().equals("ROLE_EXPERT")) {
            throw new MessageException("Bạn không có quyền trả lời tin nhắn");
        }

        // Lấy tin nhắn gốc
        Message originalMessage = messageRepository.findById(messageId)
                .orElseThrow(() -> new MessageException("Tin nhắn không tồn tại"));

        // Kiểm tra expert có phải người nhận tin nhắn gốc không
        if (!originalMessage.getReceiver().getId().equals(currentUser.getId())) {
            throw new MessageException("Bạn không có quyền trả lời tin nhắn này");
        }

        // Đánh dấu tin nhắn gốc đã đọc
        originalMessage.setIsRead(true);
        messageRepository.save(originalMessage);

        // Tạo tin nhắn trả lời
        Message reply = new Message();
        reply.setSender(currentUser); // Expert là người gửi
        reply.setReceiver(originalMessage.getSender()); // User là người nhận
        reply.setContent(content);
        reply.setMessageType(messageType != null ? messageType : "text");
        reply.setFileUrl(fileUrl);
        reply.setFileName(fileName);
        reply.setFileType(fileType);
        reply.setIsRead(false);

        Message savedReply = messageRepository.save(reply);

        // Gửi email thông báo cho User (async, không block)
        notificationService.notifyNewReply(savedReply);

        return savedReply;
    }

    // Lấy conversation giữa user và expert
    public List<Message> getConversation(Long expertUserId) {
        User currentUser = userUtils.getUserWithAuthority();
        if (currentUser == null) {
            throw new MessageException("Người dùng chưa đăng nhập");
        }

        User expert = userRepository.findById(expertUserId)
                .orElseThrow(() -> new MessageException("Chuyên gia không tồn tại"));

        return messageRepository.findConversation(currentUser, expert);
    }

    // Expert xem danh sách tin nhắn đến
    public Page<Message> getInboxMessages(Pageable pageable) {
        User currentUser = userUtils.getUserWithAuthority();
        if (currentUser == null) {
            throw new MessageException("Chuyên gia chưa đăng nhập");
        }

        if (currentUser.getAuthorities() == null || !currentUser.getAuthorities().getName().equals("ROLE_EXPERT")) {
            throw new MessageException("Bạn không có quyền xem tin nhắn");
        }

        return messageRepository.findByReceiver(currentUser, pageable);
    }

    // User xem danh sách tin nhắn đã gửi
    public Page<Message> getSentMessages(Pageable pageable) {
        User currentUser = userUtils.getUserWithAuthority();
        if (currentUser == null) {
            throw new MessageException("Người dùng chưa đăng nhập");
        }

        return messageRepository.findBySender(currentUser, pageable);
    }

    // Đếm số tin nhắn chưa đọc (cho expert)
    public Long countUnreadMessages() {
        User currentUser = userUtils.getUserWithAuthority();
        if (currentUser == null) {
            return 0L;
        }

        if (currentUser.getAuthorities() == null || !currentUser.getAuthorities().getName().equals("ROLE_EXPERT")) {
            return 0L;
        }

        return messageRepository.countUnreadMessages(currentUser);
    }

    // Lấy danh sách người đã nhắn tin với expert
    public List<User> getConversationPartners() {
        User currentUser = userUtils.getUserWithAuthority();
        if (currentUser == null) {
            throw new MessageException("Chuyên gia chưa đăng nhập");
        }

        if (currentUser.getAuthorities() == null || !currentUser.getAuthorities().getName().equals("ROLE_EXPERT")) {
            throw new MessageException("Bạn không có quyền xem danh sách này");
        }

        // Lấy cả người đã gửi tin nhắn đến expert và người đã nhận tin nhắn từ expert
        List<User> senders = messageRepository.findSendersToExpert(currentUser);
        List<User> receivers = messageRepository.findReceiversFromExpert(currentUser);
        
        // Merge và loại bỏ duplicate
        Set<User> uniquePartners = new HashSet<>();
        uniquePartners.addAll(senders);
        uniquePartners.addAll(receivers);
        
        return new ArrayList<>(uniquePartners);
    }

    // Expert lấy conversation với user cụ thể
    public List<Message> getExpertConversation(Long userId) {
        User currentUser = userUtils.getUserWithAuthority();
        if (currentUser == null) {
            throw new MessageException("Chuyên gia chưa đăng nhập");
        }

        if (currentUser.getAuthorities() == null || !currentUser.getAuthorities().getName().equals("ROLE_EXPERT")) {
            throw new MessageException("Bạn không có quyền xem tin nhắn này");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new MessageException("Người dùng không tồn tại"));

        return messageRepository.findConversation(currentUser, user);
    }

    // Lấy danh sách experts mà user đã chat
    public List<User> getUserConversationPartners() {
        User currentUser = userUtils.getUserWithAuthority();
        if (currentUser == null) {
            throw new MessageException("Người dùng chưa đăng nhập");
        }

        if (currentUser.getAuthorities() == null || !currentUser.getAuthorities().getName().equals("ROLE_USER")) {
            throw new MessageException("Bạn không có quyền xem danh sách này");
        }

        // Lấy cả expert đã gửi tin nhắn đến user và expert đã nhận tin nhắn từ user
        List<User> senders = messageRepository.findSendersToUser(currentUser);
        List<User> receivers = messageRepository.findReceiversFromUser(currentUser);
        
        // Merge và loại bỏ duplicate
        Set<User> uniquePartners = new HashSet<>();
        uniquePartners.addAll(senders);
        uniquePartners.addAll(receivers);
        
        return new ArrayList<>(uniquePartners);
    }

    // Đếm số tin nhắn chưa đọc (cho user)
    public Long countUserUnreadMessages() {
        User currentUser = userUtils.getUserWithAuthority();
        if (currentUser == null) {
            return 0L;
        }

        if (currentUser.getAuthorities() == null || !currentUser.getAuthorities().getName().equals("ROLE_USER")) {
            return 0L;
        }

        return messageRepository.countUnreadMessagesForUser(currentUser);
    }

    // Đánh dấu tin nhắn đã đọc
    @Transactional
    public void markAsRead(Long messageId) {
        User currentUser = userUtils.getUserWithAuthority();
        if (currentUser == null) {
            throw new MessageException("Người dùng chưa đăng nhập");
        }

        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new MessageException("Tin nhắn không tồn tại"));

        // Chỉ người nhận mới đánh dấu đã đọc
        if (message.getReceiver().getId().equals(currentUser.getId())) {
            message.setIsRead(true);
            messageRepository.save(message);
        }
    }
}

