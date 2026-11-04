package com.csust.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class OnlineStatusService {

    private final SimpMessagingTemplate messagingTemplate;
    private final Set<Long> onlineUsers = ConcurrentHashMap.newKeySet();

    public void userConnected(Long userId) {
        onlineUsers.add(userId);
        broadcastOnlineStatus(userId, true);
        log.debug("User {} connected, online users: {}", userId, onlineUsers.size());
    }

    public void userDisconnected(Long userId) {
        onlineUsers.remove(userId);
        broadcastOnlineStatus(userId, false);
        log.debug("User {} disconnected, online users: {}", userId, onlineUsers.size());
    }

    public boolean isOnline(Long userId) {
        return onlineUsers.contains(userId);
    }

    public int getOnlineCount() {
        return onlineUsers.size();
    }

    private void broadcastOnlineStatus(Long userId, boolean online) {
        messagingTemplate.convertAndSend(
                "/topic/online",
                java.util.Map.of("userId", userId, "online", online)
        );
    }
}
