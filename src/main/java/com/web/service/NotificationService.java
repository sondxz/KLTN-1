package com.web.service;

import com.web.entity.Message;
import com.web.entity.User;
import com.web.utils.MailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    @Autowired
    private MailService mailService;

    @Autowired
    private ExpertStatusService expertStatusService;

    @Value("${app.base-url:http://localhost:8080}")
    private String appBaseUrl;

    // Rate limit cache: key = conversationKey, value = last email sent time
    private final ConcurrentHashMap<String, LocalDateTime> lastNotificationCache = new ConcurrentHashMap<>();

    private static final long RATE_LIMIT_MINUTES = 5;
    private static final int MAX_CACHE_SIZE = 10_000;

    /**
     * Cleanup cache every 30 minutes to prevent memory leak.
     */
    @Scheduled(fixedRate = 1_800_000)
    public void cleanupCache() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(RATE_LIMIT_MINUTES * 2);
        lastNotificationCache.entrySet().removeIf(e -> e.getValue().isBefore(threshold));
        log.debug("Notification cache cleanup: {} entries remaining", lastNotificationCache.size());
    }

    /**
     * Normalized conversation key — key theo hướng gửi (sender → receiver)
     * để tránh rate-limit chặn cả 2 chiều. Khi User gửi → Expert và Expert trả lời
     * → User là 2 hướng khác nhau, mỗi hướng có rate limit riêng.
     */
    private String conversationKey(Long senderId, Long receiverId) {
        return senderId + "_to_" + receiverId;
    }

    /**
     * Notify Expert when User sends a new message.
     */
    @Async("emailNotificationExecutor")
    public void notifyNewMessage(Message message) {
        try {
            User receiver = message.getReceiver();
            User sender = message.getSender();

            if (receiver == null || receiver.getEmail() == null) return;

            // KHÔNG gửi email nếu người nhận đang ONLINE (đã thấy real-time qua WebSocket)
            if (expertStatusService.isOnline(String.valueOf(receiver.getId()))) {
                log.info("📨 Bỏ qua email: receiver {} đang ONLINE, sẽ thấy tin nhắn real-time", receiver.getEmail());
                return;
            }

            String key = conversationKey(sender.getId(), receiver.getId());
            if (!shouldSend(key)) return;

            String senderName = sender.getFullname() != null ? sender.getFullname() : "Người dùng";
            String subject = "[DuocLieuVN] " + senderName + " đã gửi cho bạn một tin nhắn mới";
            String content = buildNewMessageEmail(message);

            mailService.sendEmail(receiver.getEmail(), subject, content, false, true);
            lastNotificationCache.put(key, LocalDateTime.now());
            log.info("Đã gửi email thông báo tin nhắn mới đến: {} (receiver OFFLINE)", receiver.getEmail());

        } catch (Exception e) {
            log.error("Lỗi gửi email thông báo tin nhắn mới: {}", e.getMessage());
        }
    }

    /**
     * Notify User when Expert replies to their message.
     */
    @Async("emailNotificationExecutor")
    public void notifyNewReply(Message replyMessage) {
        try {
            User receiver = replyMessage.getReceiver();
            User sender = replyMessage.getSender();

            if (receiver == null || receiver.getEmail() == null) return;

            // KHÔNG gửi email nếu người nhận đang ONLINE (đã thấy real-time qua WebSocket)
            if (expertStatusService.isOnline(String.valueOf(receiver.getId()))) {
                log.info("📨 Bỏ qua email: receiver {} đang ONLINE, sẽ thấy tin nhắn real-time", receiver.getEmail());
                return;
            }

            String key = conversationKey(sender.getId(), receiver.getId());
            if (!shouldSend(key)) return;

            String expertName = sender.getFullname() != null ? sender.getFullname() : "Chuyên gia";
            String subject = "[DuocLieuVN] Chuyên gia " + expertName + " đã trả lời tin nhắn của bạn";
            String content = buildNewReplyEmail(replyMessage);

            mailService.sendEmail(receiver.getEmail(), subject, content, false, true);
            lastNotificationCache.put(key, LocalDateTime.now());
            log.info("Đã gửi email thông báo phản hồi đến: {} (receiver OFFLINE)", receiver.getEmail());

        } catch (Exception e) {
            log.error("Lỗi gửi email thông báo phản hồi: {}", e.getMessage());
        }
    }

    private boolean shouldSend(String conversationKey) {
        if (lastNotificationCache.size() > MAX_CACHE_SIZE) {
            cleanupCache();
        }
        LocalDateTime lastSent = lastNotificationCache.get(conversationKey);
        if (lastSent == null) return true;
        return LocalDateTime.now().isAfter(lastSent.plusMinutes(RATE_LIMIT_MINUTES));
    }

    private String buildNewMessageEmail(Message message) {
        User sender = message.getSender();
        String senderName = sender.getFullname() != null ? sender.getFullname() : "Người dùng";
        String messagePreview = truncate(message.getContent(), 100);
        // Link trực tiếp đến conversation với user này
        String chatUrl = appBaseUrl + "/user/messages?userId=" + sender.getId();

        return loadEmailTemplate(
            "Tin nhắn mới từ " + senderName,
            senderName + " vừa gửi cho bạn một tin nhắn mới",
            messagePreview,
            (message.getMessageType() != null && message.getMessageType().equals("image"))
                ? "📷 Hình ảnh" : "💬 Tin nhắn",
            chatUrl,
            "Xem tin nhắn ngay"
        );
    }

    private String buildNewReplyEmail(Message reply) {
        User expert = reply.getSender();
        String expertName = expert.getFullname() != null ? expert.getFullname() : "Chuyên gia";
        String messagePreview = truncate(reply.getContent(), 100);
        // Link trực tiếp đến conversation với expert này
        String chatUrl = appBaseUrl + "/user/messages?userId=" + expert.getId();

        return loadEmailTemplate(
            "Chuyên gia " + expertName + " đã trả lời bạn",
            "Chuyên gia <b>" + expertName + "</b> vừa trả lời tin nhắn của bạn",
            messagePreview,
            "💬 Phản hồi từ chuyên gia",
            chatUrl,
            "Xem phản hồi"
        );
    }

    private String truncate(String text, int maxLen) {
        if (text == null || text.isEmpty()) return "(không có nội dung)";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }

    private String loadEmailTemplate(String title, String subtitle,
            String preview, String badge, String actionUrl, String actionText) {
        return """
        <!DOCTYPE html>
        <html lang="vi">
        <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        </head>
        <body style="margin:0;padding:0;background-color:#f0fdf4;font-family:Arial,sans-serif;">
        <table width="100%%" cellpadding="0" cellspacing="0" style="background-color:#f0fdf4;padding:20px 0;">
          <tr>
            <td align="center">
              <table width="600" cellpadding="0" cellspacing="0" style="max-width:600px;background:#ffffff;border-radius:12px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,0.08);">
                <tr>
                  <td style="background:linear-gradient(135deg,#16a34a,#15803d);padding:24px 32px;text-align:center;">
                    <h1 style="color:#ffffff;font-size:20px;margin:0;font-weight:700;">🌿 DuocLieuVN</h1>
                    <p style="color:#dcfce7;font-size:13px;margin:6px 0 0;">Hệ thống Quản lý Cây Dược Liệu</p>
                  </td>
                </tr>
                <tr>
                  <td style="padding:32px;">
                    <p style="font-size:16px;color:#1f2937;margin:0 0 8px;font-weight:600;">%s</p>
                    <p style="font-size:14px;color:#6b7280;margin:0 0 20px;">%s</p>
                    <span style="display:inline-block;background:#dcfce7;color:#16a34a;padding:4px 12px;border-radius:20px;font-size:12px;font-weight:600;margin-bottom:16px;">%s</span>
                    <div style="background:#f9fafb;border:1px solid #e5e7eb;border-radius:8px;padding:16px;margin-bottom:24px;">
                      <p style="font-size:14px;color:#374151;margin:0;line-height:1.5;">%s</p>
                    </div>
                    <table width="100%%" cellpadding="0" cellspacing="0">
                      <tr>
                        <td align="center">
                          <a href="%s" style="display:inline-block;background:#16a34a;color:#ffffff;text-decoration:none;padding:14px 36px;border-radius:8px;font-size:15px;font-weight:700;">
                            %s →
                          </a>
                        </td>
                      </tr>
                    </table>
                    <p style="font-size:12px;color:#9ca3af;margin:20px 0 0;text-align:center;">
                      Hoặc copy link này vào trình duyệt:<br>
                      <span style="color:#16a34a;">%s</span>
                    </p>
                  </td>
                </tr>
                <tr>
                  <td style="background:#f9fafb;padding:20px 32px;text-align:center;border-top:1px solid #e5e7eb;">
                    <p style="font-size:11px;color:#9ca3af;margin:0;">
                      © 2026 DuocLieuVN. Email được gửi tự động, vui lòng không trả lời.<br>
                      Đây là email thông báo từ hệ thống chat với chuyên gia.
                    </p>
                  </td>
                </tr>
              </table>
            </td>
          </tr>
        </table>
        </body>
        </html>
        """.formatted(title, subtitle, badge, preview, actionUrl, actionText, actionUrl);
    }
}
