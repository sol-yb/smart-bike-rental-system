package com.smartbike.rental.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartbike.rental.config.WebSocketHandler;
import com.smartbike.rental.model.Notification;
import com.smartbike.rental.model.User;
import com.smartbike.rental.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class NotificationService {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private WebSocketHandler webSocketHandler;

    @Autowired
    private ObjectMapper objectMapper;

    @Transactional
    public Notification createNotification(User user, String title, String message, String type) {
        Notification notification = Notification.builder()
                .user(user)
                .title(title)
                .message(message)
                .type(type)
                .read(false)
                .build();

        notification = notificationRepository.save(notification);

        pushWebSocketNotification(notification);

        return notification;
    }

    public List<Notification> getNotificationsForUser(UUID userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public List<Notification> getGlobalNotifications() {
        return notificationRepository.findByUserIdIsNullOrderByCreatedAtDesc();
    }

    @Transactional
    public void markAsRead(UUID id) {
        notificationRepository.findById(id).ifPresent(notification -> {
            notification.setRead(true);
            notificationRepository.save(notification);
        });
    }

    private void pushWebSocketNotification(Notification notification) {
        try {
            Map<String, Object> wsMessage = new HashMap<>();
            wsMessage.put("event", "NOTIFICATION");
            wsMessage.put("id", notification.getId().toString());
            wsMessage.put("title", notification.getTitle());
            wsMessage.put("message", notification.getMessage());
            wsMessage.put("type", notification.getType());
            wsMessage.put("userId", notification.getUser() != null ? notification.getUser().getId().toString() : null);

            String jsonPayload = objectMapper.writeValueAsString(wsMessage);
            webSocketHandler.broadcast(jsonPayload);
        } catch (Exception e) {
            // Silent catch
        }
    }
}
