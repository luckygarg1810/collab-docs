package com.project.collab_docs.security;

import com.project.collab_docs.entities.User;
import io.jsonwebtoken.Claims;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

@Getter
public class CustomUserDetails implements UserDetails {
    private final User user;
    private final Collection<? extends GrantedAuthority> authorities;

    public CustomUserDetails(User user) {
        this.user = user;
        this.authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));
    }

    public CustomUserDetails(Claims claims) {
        this.user = new User();
        user.setEmail(claims.getSubject());
        user.setFirstName((String) claims.get("firstName"));
        user.setLastName((String) claims.get("lastName"));
        // We don't have the ID or password here, which is fine for authorization

        String roles = (String) claims.get("roles");
        this.authorities = Arrays.stream(roles.split(","))
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // If you have roles in your User entity, you can map them here
        // For now, returning a default role or empty list
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));

        // If you have roles in your User entity, use something like:
        // return user.getRoles().stream()
        //     .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName().toUpperCase()))
        //     .collect(Collectors.toSet());
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }

    @Override
    public boolean isAccountNonLocked() {
        // You can add logic here to check if account is locked
        // For now, assuming all accounts are non-locked
        return true;
    }

    // Convenience methods to access user data
    public Long getId() {
        return user.getId();
    }

    public String getEmail() {
        return user.getEmail();
    }

    public String getFirstName() {
        return user.getFirstName();
    }

    public String getLastName() {
        return user.getLastName();
    }
}
