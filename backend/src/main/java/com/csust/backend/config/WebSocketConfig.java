package com.csust.backend.config;

import com.csust.backend.security.JwtUtil;
import com.csust.backend.security.TokenBlacklistService;
import com.csust.backend.service.OnlineStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import java.security.Principal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor webSocketAuthInterceptor;
    private final ApplicationContext applicationContext;
    private final JwtUtil jwtUtil;
    private final TokenBlacklistService tokenBlacklistService;

    @Value("${app.websocket.allowed-origins:http://localhost:*}")
    private String[] allowedOrigins;

    private static final int MAX_CONNECTIONS = 2000;

    private final AtomicInteger connectionCount = new AtomicInteger(0);

    private OnlineStatusService getOnlineStatusService() {
        return applicationContext.getBean(OnlineStatusService.class);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .addInterceptors(webSocketAuthInterceptor)
                .setAllowedOriginPatterns(allowedOrigins)
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String authHeader = accessor.getFirstNativeHeader("Authorization");
                    if (authHeader != null && authHeader.startsWith("Bearer ")) {
                        String token = authHeader.substring(7);
                        if (jwtUtil.validateToken(token) && !tokenBlacklistService.isBlacklisted(token)) {
                            Long userId = jwtUtil.getUserIdFromToken(token);
                            Principal principal = new UsernamePasswordAuthenticationToken(
                                    userId.toString(), null,
                                    List.of(new SimpleGrantedAuthority("ROLE_USER"))
                            );
                            accessor.setUser(principal);
                            accessor.getSessionAttributes().put("userId", userId);
                            getOnlineStatusService().userConnected(userId);
                            log.debug("WebSocket CONNECT authenticated for userId={}", userId);
                        } else {
                            throw new MessagingException("Unauthorized: invalid or blacklisted token");
                        }
                    } else {
                        throw new MessagingException("Unauthorized: missing Authorization header");
                    }
                }
                if (accessor != null && StompCommand.DISCONNECT.equals(accessor.getCommand())) {
                    Object userId = accessor.getSessionAttributes().get("userId");
                    if (userId != null) {
                        getOnlineStatusService().userDisconnected(Long.valueOf(userId.toString()));
                    }
                }
                return message;
            }
        });
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        // 消息大小限制：单条 64KB，发送缓冲区 512KB，发送超时 20s
        registration.setMessageSizeLimit(64 * 1024);
        registration.setSendBufferSizeLimit(512 * 1024);
        registration.setSendTimeLimit(20 * 1000);

        registration.addDecoratorFactory(wsHandler -> new WebSocketHandlerDecorator(wsHandler) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                if (connectionCount.incrementAndGet() > MAX_CONNECTIONS) {
                    connectionCount.decrementAndGet();
                    session.close(CloseStatus.NOT_ACCEPTABLE);
                    log.warn("WebSocket connection rejected: max connections ({}) exceeded", MAX_CONNECTIONS);
                    return;
                }
                super.afterConnectionEstablished(session);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
                connectionCount.decrementAndGet();
                Object userId = session.getAttributes().get("userId");
                if (userId != null) {
                    getOnlineStatusService().userDisconnected(Long.valueOf(userId.toString()));
                }
                super.afterConnectionClosed(session, closeStatus);
            }
        });
    }
}
