package com.masterjp.security;

import com.masterjp.domain.User;
import com.masterjp.repository.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Doc Bearer token (neu co), xac thuc va nap UserPrincipal vao SecurityContext.
 * Token sai het han -> giu anonymous, KHONG nem loi o day; route can auth se
 * bi AuthenticationEntryPoint tra 401 {code,message}.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    public JwtAuthFilter(JwtUtil jwtUtil, UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            if (!token.isEmpty()) {
                try {
                    Claims claims = jwtUtil.parse(token);
                    String userId = claims.getSubject();
                    String role = String.valueOf(claims.get("role"));
                    User user = userRepository.findById(userId).orElse(null);
                    if (user != null && user.getRole().name().equals(role)) {
                        UserPrincipal principal = new UserPrincipal(user.getId(), user.getEmail(), role);
                        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                principal, null, principal.getAuthorities());
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                } catch (Exception ignored) {
                    // token khong hop le -> anonymous
                }
            }
        }
        chain.doFilter(request, response);
    }
}
