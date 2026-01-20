package com.web.repository;

import com.web.entity.Message;
import com.web.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    
    // Lấy tất cả tin nhắn giữa 2 user (conversation)
    @Query("SELECT m FROM Message m " +
           "LEFT JOIN FETCH m.sender " +
           "LEFT JOIN FETCH m.receiver " +
           "WHERE (m.sender = :user1 AND m.receiver = :user2) OR (m.sender = :user2 AND m.receiver = :user1) " +
           "ORDER BY m.createdAt ASC")
    List<Message> findConversation(@Param("user1") User user1, @Param("user2") User user2);
    
    // Lấy danh sách tin nhắn đến (cho expert)
    @Query("SELECT m FROM Message m WHERE m.receiver = :receiver ORDER BY m.createdAt DESC")
    Page<Message> findByReceiver(@Param("receiver") User receiver, Pageable pageable);
    
    // Lấy danh sách tin nhắn đã gửi (cho user)
    @Query("SELECT m FROM Message m WHERE m.sender = :sender ORDER BY m.createdAt DESC")
    Page<Message> findBySender(@Param("sender") User sender, Pageable pageable);
    
    // Đếm số tin nhắn chưa đọc
    @Query("SELECT COUNT(m) FROM Message m WHERE m.receiver = :receiver AND m.isRead = false")
    Long countUnreadMessages(@Param("receiver") User receiver);
    
    // Lấy danh sách người đã gửi tin nhắn đến expert
    @Query("SELECT DISTINCT m.sender FROM Message m WHERE m.receiver = :expert")
    List<User> findSendersToExpert(@Param("expert") User expert);
    
    // Lấy danh sách người đã nhận tin nhắn từ expert
    @Query("SELECT DISTINCT m.receiver FROM Message m WHERE m.sender = :expert")
    List<User> findReceiversFromExpert(@Param("expert") User expert);
    
    // Lấy danh sách experts đã gửi tin nhắn đến user
    @Query("SELECT DISTINCT m.sender FROM Message m WHERE m.receiver = :user AND m.sender.authorities.name = 'ROLE_EXPERT'")
    List<User> findSendersToUser(@Param("user") User user);
    
    // Lấy danh sách experts đã nhận tin nhắn từ user
    @Query("SELECT DISTINCT m.receiver FROM Message m WHERE m.sender = :user AND m.receiver.authorities.name = 'ROLE_EXPERT'")
    List<User> findReceiversFromUser(@Param("user") User user);
    
    // Đếm số tin nhắn chưa đọc cho user
    @Query("SELECT COUNT(m) FROM Message m WHERE m.receiver = :user AND m.isRead = false")
    Long countUnreadMessagesForUser(@Param("user") User user);
}

