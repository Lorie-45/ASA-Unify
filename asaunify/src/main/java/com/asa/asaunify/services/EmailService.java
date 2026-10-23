package com.asa.asaunify.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Outbound transactional email.
 *
 * Sending is asynchronous so the caller's response time does not depend on
 * SMTP — that matters for the forgot-password endpoint, where a slower
 * response for real accounts would itself leak which addresses exist.
 *
 * A delivery failure is logged and swallowed rather than propagated, for the
 * same reason: the API response must be identical whether or not an account
 * was found and whether or not the mail server accepted the message.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from:no-reply@asaunify.local}")
    private String fromAddress;

    @Value("${app.password-reset.code-expiry-minutes:10}")
    private long codeExpiryMinutes;

    /**
     * Sends a password-reset code. The code is passed to the mail body and
     * never written to a log, an audit entry, or an API response.
     */
    @Async
    public void sendPasswordResetCode(
            String toAddress,
            String fullName,
            String code) {

        String body = """
                Hello %s,

                We received a request to reset your ASAUnify password.

                Your verification code is: %s

                This code expires in %d minutes and can be used only once.

                If you did not request a password reset, you can ignore this
                email — your password has not been changed.

                — ASAUnify
                """.formatted(fullName, code, codeExpiryMinutes);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toAddress);
        message.setSubject("Your ASAUnify password reset code");
        message.setText(body);

        try {
            mailSender.send(message);
            // Recipient is logged for operational traceability; the code is not.
            log.info("Password reset code dispatched to {}", toAddress);
        } catch (MailException e) {
            // Never rethrow: the caller's response must not reveal delivery
            // outcome. Message only — a stack trace here adds nothing but noise.
            log.error("Failed to send password reset email to {}: {}",
                    toAddress, e.getMessage());
        }
    }
}
