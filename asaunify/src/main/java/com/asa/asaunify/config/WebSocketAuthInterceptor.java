package com.asa.asaunify.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Authenticates the STOMP session. The HTTP handshake at /ws is open (browsers
 * cannot set an Authorization header on a WebSocket/SockJS request), so the
 * access token is presented in the STOMP CONNECT frame instead and checked with
 * the same rules JwtFilter applies to REST calls: access token only, not
 * revoked, not older than the last password change, account still active.
 *
 * The session principal is named after the user's id, which is what
 * NotificationService addresses via convertAndSendToUser(userId, ...).
 *
 * Authorisation is deliberately narrow: clients may only subscribe to their own
 * /user/queue/notifications and may not publish anything.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    static final String NOTIFICATIONS_DESTINATION = "/user/queue/notifications";

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;
    private final TokenRevocationService tokenRevocationService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor
                .getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();

        if (StompCommand.CONNECT.equals(command)) {
            accessor.setUser(authenticate(
                    accessor.getFirstNativeHeader("Authorization")));
            return message;
        }

        if (StompCommand.SUBSCRIBE.equals(command)) {
            if (accessor.getUser() == null
                    || !NOTIFICATIONS_DESTINATION.equals(accessor.getDestination())) {
                throw new MessagingException("Subscription not permitted");
            }
            return message;
        }

        if (StompCommand.SEND.equals(command)) {
            throw new MessagingException("Publishing is not permitted");
        }

        return message;
    }

    private UsernamePasswordAuthenticationToken authenticate(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            throw new MessagingException("Authentication required");
        }
        String token = header.substring(7);

        try {
            if (!jwtUtil.isAccessToken(token)
                    || jwtUtil.isTokenExpired(token)
                    || tokenRevocationService.isRevoked(jwtUtil.extractJti(token))) {
                throw new MessagingException("Authentication required");
            }

            UserDetails user = userDetailsService
                    .loadUserByUsername(jwtUtil.extractEmail(token));

            if (user instanceof AppUserDetails appUser
                    && appUser.getPasswordChangedAt() != null) {
                Instant issuedAt = jwtUtil.extractIssuedAt(token);
                if (issuedAt == null
                        || issuedAt.isBefore(appUser.getPasswordChangedAt())) {
                    throw new MessagingException("Authentication required");
                }
            }

            // Principal name = user id, matching convertAndSendToUser(userId).
            return new UsernamePasswordAuthenticationToken(
                    jwtUtil.extractUserId(token), null, user.getAuthorities());

        } catch (MessagingException e) {
            throw e;
        } catch (Exception e) {
            // Bad signature, malformed, unknown or disabled user, ...
            log.debug("WebSocket authentication failed: {}", e.getMessage());
            throw new MessagingException("Authentication required");
        }
    }
}
