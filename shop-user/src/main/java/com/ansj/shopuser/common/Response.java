package com.ansj.shopuser.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class Response <T> {
    private int code;
    private String msg;
    private T data;

    private Response(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public static <T> Response<T> badRequest(String msg) {
        return new Response<>(400, msg);
    }
}
