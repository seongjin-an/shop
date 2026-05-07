package com.ansj.shopuser.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

public class GatewayUserContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
        FilterChain filterChain) throws ServletException, IOException {

        String userIdStr = request.getHeader("X-User-Id");
        String role = request.getHeader("X-User-Role");

        if (StringUtils.hasText(userIdStr)) {
            var authorities = StringUtils.hasText(role)
                ? List.of(new SimpleGrantedAuthority(role))
                : List.<SimpleGrantedAuthority>of();
            var auth = new UsernamePasswordAuthenticationToken(
                Long.valueOf(userIdStr), null, authorities);
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        filterChain.doFilter(request, response);
    }
}
