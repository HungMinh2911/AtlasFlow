package com.atlasflow.security.user;

import com.atlasflow.users.domain.User;
import com.atlasflow.users.domain.UserStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

public class CustomUserDetails implements UserDetails {

    private final User user;
    private final Collection<? extends GrantedAuthority> authorities;

    public CustomUserDetails(User user) {
        this(user, Collections.emptyList());
    }

    public CustomUserDetails(User user, Collection<? extends GrantedAuthority> authorities) {
        this.user = user;
        this.authorities = authorities != null ? authorities : Collections.emptyList();
    }

    public User getUser() {
        return user;
    }

    public UUID getId() {
        return user.id();
    }

    @Override
    public String getUsername() {
        return user.email();
    }

    @Override
    public String getPassword() {
        return user.passwordHash();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return user.status() != UserStatus.LOCKED;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return user.status() == UserStatus.ACTIVE;
    }
}
