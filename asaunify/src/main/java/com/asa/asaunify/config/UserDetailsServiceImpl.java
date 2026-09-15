package com.asa.asaunify.config;


import com.asa.asaunify.entity.User;
import com.asa.asaunify.repos.UserRepo;
import com.asa.asaunify.util.EmailNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepo userRepository;

    @Override
    public UserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {

        // Normalized so a lookup can never miss on casing or stray whitespace
        // — addresses are stored in canonical form.
        String normalizedEmail = EmailNormalizer.normalize(email);

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found with email: " + normalizedEmail
                ));

        // Check if user is active — deactivated users cannot log in
        if (!user.isActive()) {
            throw new UsernameNotFoundException(
                    "User account is deactivated: " + email
            );
        }

        // Convert our Role enum to Spring Security's GrantedAuthority
        // Spring Security expects roles prefixed with "ROLE_"
        // e.g. Role.ADMIN becomes "ROLE_ADMIN"
        // This is what @PreAuthorize("hasRole('ADMIN')") checks against
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority(
                "ROLE_" + user.getRole().name()
        );

        // AppUserDetails carries passwordChangedAt so JwtFilter can reject
        // tokens minted before the last password change. Same flags as before.
        return new AppUserDetails(
                user.getEmail(),
                user.getPasswordHash(),
                user.isActive(),
                user.isActive(),
                List.of(authority),
                user.getPasswordChangedAt()
        );
    }
}