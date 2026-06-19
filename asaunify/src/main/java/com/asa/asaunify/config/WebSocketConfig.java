package com.asa.asaunify.config;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${websocket.allowed-origins}")
    private String allowedOrigin;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {

        // Prefix for messages the server sends to clients
        // e.g. /topic/notifications → broadcast to all subscribers
        // e.g. /user/{id}/queue/notifications → send to specific user
        registry.enableSimpleBroker("/topic", "/queue");

        // Prefix for messages clients send to the server
        // e.g. /app/mark-read
        registry.setApplicationDestinationPrefixes("/app");

        // Prefix for user-specific destinations
        // Spring uses this to route to /user/{sessionId}/queue/...
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {

        // The WebSocket handshake endpoint
        // React connects to: ws://localhost:8080/ws
        registry.addEndpoint("/ws")
                .setAllowedOrigins(allowedOrigin)
                // SockJS fallback for browsers that don't support WebSocket
                .withSockJS();
    }
}