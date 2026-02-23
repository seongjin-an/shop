package com.ansj.shopuser.common;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice(annotations = {RestController.class}, basePackages = {"com.ansj.shopuser.user.controller"})
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(value = {IllegalArgumentException.class, IllegalStateException.class, UsernameNotFoundException.class})
    public Response<Void> badRequestHandler(Exception e) {
        return Response.badRequest(e.getMessage());
    }
}
