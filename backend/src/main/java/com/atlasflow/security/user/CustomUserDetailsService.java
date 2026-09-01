package com.atlasflow.security.user;

import com.atlasflow.users.application.port.UserRepository;
import com.atlasflow.users.domain.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.HashSet;
import java.util.Set;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final JdbcClient jdbcClient;

    public CustomUserDetailsService(UserRepository userRepository, JdbcClient jdbcClient) {
        this.userRepository = userRepository;
        this.jdbcClient = jdbcClient;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        Set<SimpleGrantedAuthority> authorities = new HashSet<>();
        jdbcClient.sql("""
                SELECT r.name
                FROM memberships m
                JOIN roles r ON r.id = m.role_id
                WHERE m.user_id = :userId
                  AND m.status = 'ACTIVE'
                """)
                .param("userId", user.id())
                .query(String.class)
                .list()
                .forEach(roleName -> {
                    try {
                        var role = com.atlasflow.security.rbac.PredefinedRole.valueOf(roleName.trim().toUpperCase());
                        authorities.addAll(role.getAuthorities());
                    } catch (IllegalArgumentException ignored) {
                        // Custom roles are intentionally not granted platform permissions implicitly.
                    }
                });
        return new CustomUserDetails(user, authorities);
    }
}
