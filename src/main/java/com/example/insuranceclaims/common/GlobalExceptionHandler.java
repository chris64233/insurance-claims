package com.example.insuranceclaims.common;

import com.example.insuranceclaims.claim.DuplicateClaimException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DuplicateClaimException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError handleDuplicateClaim(DuplicateClaimException ex) {
        return ApiError.of(HttpStatus.CONFLICT.value(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        String message = fieldErrors.values().stream().findFirst().orElse("请求参数校验失败");
        return ApiError.of(HttpStatus.BAD_REQUEST.value(), message, fieldErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleNotReadable(HttpMessageNotReadableException ex) {
        return ApiError.of(HttpStatus.BAD_REQUEST.value(), "请求体格式有误，请检查后重试");
    }
}
