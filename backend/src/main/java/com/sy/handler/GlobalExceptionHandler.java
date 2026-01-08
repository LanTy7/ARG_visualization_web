package com.sy.handler;

import com.sy.vo.Result;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
@ResponseBody
public class GlobalExceptionHandler {

    // 处理参数校验异常
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Map<String, String>>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage()));
        // 返回400状态码，并在Result的data中包含详细的错误信息
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new Result<>(400, "Validation failed", errors));
    }

    // 处理文件上传大小超限异常
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<Void>> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex) {
        // 获取最大上传大小（字节）
        long maxSize = ex.getMaxUploadSize();
        String maxSizeStr;
        if (maxSize >= 1024 * 1024 * 1024) {
            maxSizeStr = String.format("%.1f GB", maxSize / (1024.0 * 1024.0 * 1024.0));
        } else if (maxSize >= 1024 * 1024) {
            maxSizeStr = String.format("%.1f MB", maxSize / (1024.0 * 1024.0));
        } else if (maxSize >= 1024) {
            maxSizeStr = String.format("%.1f KB", maxSize / 1024.0);
        } else {
            maxSizeStr = maxSize + " B";
        }
        
        String errorMessage = String.format("文件大小超过限制，最大允许上传 %s", maxSizeStr);
        // 返回413状态码（Payload Too Large）
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(Result.error(errorMessage));
    }

    // 处理其他所有未被特定ExceptionHandler捕获的异常
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleGeneralException(Exception ex) {
        // 在实际应用中，这里应该记录详细日志，并返回更友好的错误信息，避免暴露内部错误细节
        ex.printStackTrace(); // 打印异常堆栈到控制台，方便开发调试
        // 返回500状态码和通用的错误信息
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Result.error("An unexpected error occurred."));
    }

} 