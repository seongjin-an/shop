package com.ansj.shopuser.config;

import com.ansj.shopuser.common.CustomUserDetails;
import com.ansj.shopuser.common.LoginRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
public class RestApiLoginAuthFilter extends AbstractAuthenticationProcessingFilter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public RestApiLoginAuthFilter(RequestMatcher requiresAuthenticationRequestMatcher, AuthenticationManager authenticationManager) {
        super(requiresAuthenticationRequestMatcher, authenticationManager);
    }

    // 로그인 요청이 들어오면, 호출될 함수
    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException, IOException, ServletException {
        //return super.attemptAuthentication(request, response);
        if (!request.getContentType().startsWith(MediaType.APPLICATION_JSON_VALUE)) {
            throw new AuthenticationServiceException("지원하지 않는 타입 : " + request.getContentType());
        }

        LoginRequest loginRequest = objectMapper.readValue(request.getInputStream(), LoginRequest.class);
        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(loginRequest.username(), loginRequest.password());
        return getAuthenticationManager().authenticate(token);
    }

    @Override
    protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response, FilterChain chain, Authentication authResult) throws IOException, ServletException {
        //super.successfulAuthentication(request, response, chain, authResult);
        // auth result 가 securityContext 에 세팅은 됐으나 저장이 되지 않은 상태임..
        // security5 까진 자동 저장을 해줬는데, security6 에서 이걸 자동으로 저장하지 않는 변경사하이 있어서 명시적으로 개발자가 세팅해줘야 한다.
        SecurityContext securityContext = SecurityContextHolder.getContext();
        CustomUserDetails customUserDetails = (CustomUserDetails) authResult.getPrincipal();
        customUserDetails.erasePassword();
        securityContext.setAuthentication(authResult);
        HttpSessionSecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();
        contextRepository.saveContext(securityContext, request, response); // 이걸 실행해줘야, 현재 인증된 session 상태가 다음 request 까지 연결이 된다.

        String sessionId = request.getSession().getId();
        log.info("[successfulAuthentication] sessionId: {}", sessionId);
//        String encodedSessionId = Base64.getEncoder().encodeToString(sessionId.getBytes(StandardCharsets.UTF_8));

        //response.sendRedirect("/");

//        response.setStatus(HttpServletResponse.SC_OK);
//        response.setContentType(MediaType.TEXT_PLAIN_VALUE);
//        PrintWriter writer = response.getWriter();
//        writer.write(encodedSessionId);
//        writer.flush();
    }

    @Override
    protected void unsuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response, AuthenticationException failed) throws IOException, ServletException {
        //super.unsuccessfulAuthentication(request, response, failed);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.TEXT_PLAIN_VALUE);
        PrintWriter writer = response.getWriter();
        writer.write("인증 실패");
        writer.flush();
    }
}
