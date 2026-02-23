package com.ansj.shopuser.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
@Configuration
public class SecurityConfig {

    public static final String[] WHITE_LIST_ENDPOINTS = {
            "/swagger-ui/**", "/v3/api-docs/**",
            "/login", "/signup",
            "/api/users/signup", "/api/users/validate-username", "/api/login", "/api/logout",
            "/css/**", "/js/**", "/images/**"
    };

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationManager authenticationManager) throws Exception {
        RestApiLoginAuthFilter restApiLoginAuthFilter = new RestApiLoginAuthFilter(
                request -> "/api/login".equals(request.getRequestURI()) && "POST".equalsIgnoreCase(request.getMethod()),
                authenticationManager
        );
        http.cors(cors -> {});
        http.csrf(AbstractHttpConfigurer::disable);
        //http.httpBasic().disable();
        http.httpBasic(AbstractHttpConfigurer::disable);
        http.formLogin(AbstractHttpConfigurer::disable);
        http.requestCache(AbstractHttpConfigurer::disable);
        http.addFilterAt(restApiLoginAuthFilter, UsernamePasswordAuthenticationFilter.class);


        http.authorizeHttpRequests(auth -> auth
                        // PathPatternRequestMatcher가 내부적으로 사용됩니다.
                        .requestMatchers(WHITE_LIST_ENDPOINTS).permitAll()
                        .anyRequest().authenticated()

                //.anyRequest().denyAll()
        );
        http.logout(logout ->
                logout.logoutUrl("/api/logout").logoutSuccessHandler(this::logoutHandler)
        );
        http.exceptionHandling(exception -> exception
                .accessDeniedPage("/login")
                .authenticationEntryPoint(((request, response, authException) -> {
                    //response.sendRedirect("/login");
                })));

        return http.build();
    }

    private void logoutHandler(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        response.setContentType(MediaType.TEXT_PLAIN_VALUE);
        response.setCharacterEncoding("UTF-8");
        String message;

        if (authentication != null && authentication.isAuthenticated()) {
            response.setStatus(HttpStatus.OK.value());
            message = "로그아웃 성공";
        } else {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            message = "로그아웃 실패";
        }
        //try {
        //    response.sendRedirect("/login");
        //} catch (IOException ex) {
        //    log.error("전송 실패. 원인: {}", ex.getMessage(), ex);
        //}
    }

    @Bean
    public CookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("SESSION");
        // 이 부분이 핵심입니다.
        // true일 경우 브라우저의 값을 디코딩해서 읽으려 하므로, false로 설정해 매칭 이슈를 해결합니다.
        serializer.setUseBase64Encoding(false);
        serializer.setCookiePath("/");
        return serializer;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
