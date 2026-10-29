package com.exception;

import com.response.Result;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/** 新增标准 DTO 接口的统一参数错误响应；保持现有 Result 协议兼容前端。 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage() == null ? "参数不合法" : error.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining("；"));
        return Result.error(400, message.isBlank() ? "参数校验失败" : message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(ConstraintViolationException exception) {
        return Result.error(400, exception.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleUnreadableBody() {
        return Result.error(400, "请求内容格式不正确");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Void> handleIllegalArgument(IllegalArgumentException exception) {
        return Result.error(400, exception.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public Result<Void> handleIllegalState(IllegalStateException exception) {
        return Result.error(500, exception.getMessage());
    }

    @ExceptionHandler(WeChatAuthException.class)
    public Result<Void> handleWeChatAuth(WeChatAuthException exception) {
        return Result.error(exception.getCode(), exception.getMessage());
    }
}
