package com.exception;

public class WeChatAuthException extends RuntimeException {
    private final int code;

    public WeChatAuthException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
