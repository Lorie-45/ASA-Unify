package com.asa.asaunify.config;


import com.asa.asaunify.entity.User;
import com.asa.asaunify.repos.UserRepo;
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

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found with email: " + email
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

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(List.of(authority))
                .accountExpired(false)
                .accountLocked(!user.isActive())
                .credentialsExpired(false)
                .disabled(!user.isActive())
                .build();
    }
}