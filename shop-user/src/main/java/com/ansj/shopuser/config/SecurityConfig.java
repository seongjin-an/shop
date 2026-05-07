package com.ansj.shopuser.config;

import com.ansj.shopuser.user.service.BlacklistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Slf4j
@RequiredArgsConstructor
@Configuration
public class SecurityConfig {

    private final BlacklistService blacklistService;

    public static final String[] WHITE_LIST_ENDPOINTS = {
            "/swagger-ui/**", "/v3/api-docs/**",
            "/login", "/signup",
            "/api/users/signup", "/api/users/validate-username",
            "/api/users/login",
            "/api/users/reissue", "/api/users/logout",
            "/css/**", "/js/**", "/images/**"
    };

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        JwtBlacklistFilter jwtBlacklistFilter = new JwtBlacklistFilter(blacklistService);
        GatewayUserContextFilter gatewayUserContextFilter = new GatewayUserContextFilter();

        http.cors(cors -> {});
        http.csrf(AbstractHttpConfigurer::disable);
        http.httpBasic(AbstractHttpConfigurer::disable);
        http.formLogin(AbstractHttpConfigurer::disable);
        http.requestCache(AbstractHttpConfigurer::disable);
        http.sessionManagement(session ->
            session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        // 실행 순서: JwtBlacklistFilter → GatewayUserContextFilter → ...
        http.addFilterBefore(jwtBlacklistFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterAfter(gatewayUserContextFilter, JwtBlacklistFilter.class);

        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(WHITE_LIST_ENDPOINTS).permitAll()
                .anyRequest().authenticated()
        );
        http.exceptionHandling(exception -> exception
                .authenticationEntryPoint((request, response, authException) ->
                    response.setStatus(HttpStatus.UNAUTHORIZED.value()))
                .accessDeniedHandler((request, response, accessDeniedException) ->
                    response.setStatus(HttpStatus.FORBIDDEN.value())));

        return http.build();
    }
}
