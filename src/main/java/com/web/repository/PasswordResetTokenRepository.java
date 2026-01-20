package com.web.repository;

import com.web.entity.PasswordResetToken;
import com.web.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    /**
     * Tìm token theo token string và user
     */
    @Query("SELECT t FROM PasswordResetToken t WHERE t.token = :token AND t.user = :user")
    Optional<PasswordResetToken> findByTokenAndUser(@Param("token") String token, @Param("user") User user);

    /**
     * Tìm token hợp lệ (chưa dùng và chưa hết hạn) theo token string và user
     */
    @Query("SELECT t FROM PasswordResetToken t WHERE t.token = :token AND t.user = :user " +
           "AND t.isUsed = false AND t.expiresAt > :now")
    Optional<PasswordResetToken> findValidToken(@Param("token") String token, 
                                                 @Param("user") User user, 
                                                 @Param("now") LocalDateTime now);

    /**
     * Tìm tất cả token hợp lệ của user (chưa dùng và chưa hết hạn)
     */
    @Query("SELECT t FROM PasswordResetToken t WHERE t.user = :user " +
           "AND t.isUsed = false AND t.expiresAt > :now ORDER BY t.createdAt DESC")
    List<PasswordResetToken> findValidTokensByUser(@Param("user") User user, 
                                                     @Param("now") LocalDateTime now);

    /**
     * Xóa tất cả token đã hết hạn hoặc đã dùng (cleanup)
     */
    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.isUsed = true OR t.expiresAt < :now")
    void deleteExpiredOrUsedTokens(@Param("now") LocalDateTime now);

    /**
     * Xóa tất cả token của user (khi user đổi mật khẩu thành công)
     */
    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.user = :user")
    void deleteAllByUser(@Param("user") User user);

    /**
     * Đếm số token hợp lệ của user
     */
    @Query("SELECT COUNT(t) FROM PasswordResetToken t WHERE t.user = :user " +
           "AND t.isUsed = false AND t.expiresAt > :now")
    Long countValidTokensByUser(@Param("user") User user, @Param("now") LocalDateTime now);
}




