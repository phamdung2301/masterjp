package com.masterjp.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;

/**
 * Principal dua vao SecurityContext sau khi JWT hop le.
 * Authority LUON co prefix "ROLE_" (ROLE_ADMIN / ROLE_LEARNER) de khop hasRole().
 */
public class UserPrincipal {

    private final String id;
    private final String email;
    private final String role; // "ADMIN" | "LEARNER" (tran, khong prefix)

    public UserPrincipal(String id, String email, String role) {
        this.id = id;
        this.email = email;
        this.role = role;
    }

    public String getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }

    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
