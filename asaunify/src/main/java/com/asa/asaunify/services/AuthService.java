package com.asa.asaunify.services;

import com.asa.asaunify.config.JwtUtil;
import com.asa.asaunify.dtos.auth.AuthRequest;
import com.asa.asaunify.dtos.auth.AuthResponse;
import com.asa.asaunify.entity.LoginHistory;
import com.asa.asaunify.entity.User;
import com.asa.asaunify.enums.ActionType;
import com.asa.asaunify.logging.AuditService;
import com.asa.asaunify.repos.LoginHistoryRepo;
import com.asa.asaunify.repos.UserRepo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepo userRepository;
    private final LoginHistoryRepo loginHistoryRepository;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final AuditService auditService;


    @Transactional
    public AuthResponse login(AuthRequest request, HttpServletRequest httpRequest){

        User user = userRepository.findByEmail(request.getEmail()).orElseThrow(()-> new BadCredentialsException("Invalid email or password"));

        try{

            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(),
                            request.getPassword()
                    )
            );
            String accessToken = jwtUtil.generateAccessToken(user);
            String refreshToken = jwtUtil.generateRefreshToken(user);

            saveLoginHistory(user, httpRequest, "SUCCESS");

            auditService.log(
                    user,
                    ActionType.LOGIN,
                    "USER",
                    user.getId().toString(),
                    "AUTH",
                    httpRequest
            );

            return AuthResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .tokenType("Bearer")
                    .userId(user.getId())
                    .fullName(user.getFullName())
                    .email(user.getEmail())
                    .role(user.getRole())
                    .departmentName(user.getDepartment() != null ? user.getDepartment().getName() : null )
                    .build();

        }catch(AuthenticationException e){
            saveLoginHistory(user, httpRequest, "FAILED");

            auditService.log(
                    user,
                    ActionType.LOGIN_FAILED,
                    "USER",
                    user.getId().toString(),
                    "AUTH",
                    httpRequest
            );

            throw new BadCredentialsException("Invalid email or password");

        }

    }

    @Transactional
    public void logout(String email, HttpServletRequest httpRequest) {
        userRepository.findByEmail(email).ifPresent(user -> {

            // Find the open session and stamp logout time
            loginHistoryRepository
                    .findTopByUserAndLogoutAtIsNullOrderByLoginAtDesc(user)
                    .ifPresent(session -> {
                        session.setLogoutAt(LocalDateTime.now());
                        session.setSessionDurationMinutes(
                                Duration.between(
                                        session.getLoginAt(),
                                        session.getLogoutAt()
                                ).toMinutes()
                        );
                        loginHistoryRepository.save(session);
                    });

            auditService.log(
                    user,
                    ActionType.LOGOUT,
                    "USER",
                    user.getId().toString(),
                    "AUTH",
                    httpRequest
            );
        });
    }

    public AuthResponse refreshToken(String refreshToken) {
        String email = jwtUtil.extractEmail(refreshToken);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new BadCredentialsException("Invalid refresh token"));

        if (jwtUtil.isTokenExpired(refreshToken)) {
            throw new BadCredentialsException("Refresh token has expired");
        }

        String newAccessToken = jwtUtil.generateAccessToken(user);

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .userId(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole())
                .departmentName(
                        user.getDepartment() != null
                                ? user.getDepartment().getName()
                                : null
                )
                .build();
    }

    private void saveLoginHistory(
            User user,
            HttpServletRequest request,
            String status) {
        try {
            String userAgent = request.getHeader("User-Agent");

            LoginHistory history = LoginHistory.builder()
                    .user(user)
                    .loginAt(LocalDateTime.now())
                    .ipAddress(getClientIp(request))
                    .browser(parseBrowser(userAgent))
                    .device(parseDevice(userAgent))
                    .loginStatus(status)
                    .build();

            loginHistoryRepository.save(history);
        } catch (Exception e) {
            log.error("Failed to save login history: {}", e.getMessage());
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) ip = request.getRemoteAddr();
        if (ip != null && ip.contains(",")) ip = ip.split(",")[0].trim();
        return ip != null ? ip : "unknown";
    }

    private String parseBrowser(String userAgent) {
        if (userAgent == null) return "unknown";
        if (userAgent.contains("Chrome")) return "Chrome";
        if (userAgent.contains("Firefox")) return "Firefox";
        if (userAgent.contains("Safari")) return "Safari";
        if (userAgent.contains("Edge")) return "Edge";
        return "Other";
    }

    private String parseDevice(String userAgent) {
        if (userAgent == null) return "unknown";
        if (userAgent.contains("Mobile")) return "Mobile";
        if (userAgent.contains("Tablet")) return "Tablet";
        return "Desktop";
    }

}
