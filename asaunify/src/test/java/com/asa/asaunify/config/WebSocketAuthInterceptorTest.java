package com.asa.asaunify.config;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.security.Principal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebSocketAuthInterceptorTest {

    private final JwtUtil jwt = mock(JwtUtil.class);
    private final UserDetailsService users = mock(UserDetailsService.class);
    private final TokenRevocationService revoked = new TokenRevocationService();
    private final MessageChannel channel = mock(MessageChannel.class);
    private final WebSocketAuthInterceptor interceptor =
            new WebSocketAuthInterceptor(jwt, users, revoked);

    private Message<byte[]> frame(StompCommand cmd, String authHeader, String dest,
                                  Principal user) {
        StompHeaderAccessor a = StompHeaderAccessor.create(cmd);
        if (authHeader != null) a.setNativeHeader("Authorization", authHeader);
        if (dest != null) a.setDestination(dest);
        if (user != null) a.setUser(user);
        a.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], a.getMessageHeaders());
    }

    private void validToken() throws Exception {
        when(jwt.isAccessToken("good")).thenReturn(true);
        when(jwt.isTokenExpired("good")).thenReturn(false);
        when(jwt.extractJti("good")).thenReturn("jti-1");
        when(jwt.extractEmail("good")).thenReturn("a@b.c");
        when(jwt.extractUserId("good")).thenReturn("user-123");
        when(jwt.extractIssuedAt("good")).thenReturn(Instant.now());
        when(users.loadUserByUsername(anyString())).thenReturn(
                new AppUserDetails("a@b.c", "x", true, true,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN")), null));
    }

    @Test
    void connectWithoutTokenIsRejected() {
        assertThrows(MessagingException.class, () ->
                interceptor.preSend(frame(StompCommand.CONNECT, null, null, null), channel));
    }

    @Test
    void connectWithValidTokenSetsPrincipalToUserId() throws Exception {
        validToken();
        Message<?> out = interceptor.preSend(
                frame(StompCommand.CONNECT, "Bearer good", null, null), channel);
        Principal p = StompHeaderAccessor.wrap(out).getUser();
        assertEquals("user-123", p.getName());
    }

    @Test
    void refreshTokenCannotConnect() throws Exception {
        validToken();
        when(jwt.isAccessToken("good")).thenReturn(false);
        assertThrows(MessagingException.class, () ->
                interceptor.preSend(frame(StompCommand.CONNECT, "Bearer good", null, null), channel));
    }

    @Test
    void revokedTokenCannotConnect() throws Exception {
        validToken();
        revoked.revoke("jti-1", Instant.now().plusSeconds(60));
        assertThrows(MessagingException.class, () ->
                interceptor.preSend(frame(StompCommand.CONNECT, "Bearer good", null, null), channel));
    }

    @Test
    void tokenIssuedBeforePasswordChangeCannotConnect() throws Exception {
        validToken();
        when(jwt.extractIssuedAt("good")).thenReturn(Instant.now().minusSeconds(100));
        when(users.loadUserByUsername(anyString())).thenReturn(
                new AppUserDetails("a@b.c", "x", true, true,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN")), Instant.now()));
        assertThrows(MessagingException.class, () ->
                interceptor.preSend(frame(StompCommand.CONNECT, "Bearer good", null, null), channel));
    }

    @Test
    void onlyOwnNotificationQueueMaySubscribe() {
        Principal user = () -> "user-123";
        interceptor.preSend(frame(StompCommand.SUBSCRIBE, null,
                "/user/queue/notifications", user), channel);
        assertThrows(MessagingException.class, () ->
                interceptor.preSend(frame(StompCommand.SUBSCRIBE, null, "/topic/anything", user), channel));
        assertThrows(MessagingException.class, () ->
                interceptor.preSend(frame(StompCommand.SUBSCRIBE, null,
                        "/user/queue/notifications", null), channel));
    }

    @Test
    void clientsCannotPublish() {
        assertThrows(MessagingException.class, () ->
                interceptor.preSend(frame(StompCommand.SEND, null, "/app/x", () -> "u"), channel));
    }
}
