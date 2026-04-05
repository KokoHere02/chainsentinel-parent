package com.chainsentinel.web.api.support;

import com.chainsentinel.core.exception.AppException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(
		MethodArgumentNotValidException ex,
		HttpServletRequest request
	) {
		List<String> details = ex.getBindingResult().getFieldErrors().stream()
			.map(this::toFieldError)
			.collect(Collectors.toList());
		return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", request, details);
	}

	@ExceptionHandler(BindException.class)
	public ResponseEntity<ApiErrorResponse> handleBindException(
		BindException ex,
		HttpServletRequest request
	) {
		List<String> details = ex.getBindingResult().getFieldErrors().stream()
			.map(this::toFieldError)
			.collect(Collectors.toList());
		return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request binding failed", request, details);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
		ConstraintViolationException ex,
		HttpServletRequest request
	) {
		List<String> details = ex.getConstraintViolations().stream()
			.map(v -> v.getPropertyPath() + ": " + v.getMessage())
			.toList();
		return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", request, details);
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiErrorResponse> handleMethodArgumentTypeMismatch(
		MethodArgumentTypeMismatchException ex,
		HttpServletRequest request
	) {
		String detail = ex.getName() + ": invalid value " + ex.getValue();
		return build(
		HttpStatus.BAD_REQUEST,
		"TYPE_MISMATCH",
		"Request parameter type mismatch",
		request,
		List.of(detail)
		);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiErrorResponse> handleHttpMessageNotReadable(
		HttpMessageNotReadableException ex,
		HttpServletRequest request
	) {
		return build(
		HttpStatus.BAD_REQUEST,
		"INVALID_REQUEST_BODY",
		"Request body is invalid or unreadable",
		request,
		List.of()
		);
	}

	@ExceptionHandler(AppException.class)
	public ResponseEntity<ApiErrorResponse> handleAppException(AppException ex, HttpServletRequest request) {
		HttpStatus status = HttpStatus.valueOf(ex.getStatus());
		return build(status, ex.getCode(), ex.getMessage(), request, List.of());
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
		IllegalArgumentException ex,
		HttpServletRequest request
	) {
		return build(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", ex.getMessage(), request, List.of());
	}

	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<ApiErrorResponse> handleResponseStatus(
		ResponseStatusException ex,
		HttpServletRequest request
	) {
		HttpStatusCode statusCode = ex.getStatusCode();
		HttpStatus status = HttpStatus.valueOf(statusCode.value());
		String message = ex.getReason() == null ? status.getReasonPhrase() : ex.getReason();
		return build(status, "HTTP_ERROR", message, request, List.of());
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiErrorResponse> handleUnhandled(Exception ex, HttpServletRequest request) {
		log.error("Unhandled exception path={} message={}", request.getRequestURI(), ex.getMessage(), ex);
		return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Internal server error", request, List.of());
	}

	private String toFieldError(FieldError fieldError) {
		String message = fieldError.getDefaultMessage() == null ? "invalid" : fieldError.getDefaultMessage();
		return fieldError.getField() + ": " + message;
	}

	private ResponseEntity<ApiErrorResponse> build(
		HttpStatus status,
		String code,
		String message,
		HttpServletRequest request,
		List<String> details
	) {
		ApiErrorResponse body = ApiErrorResponse.of(
			status.value(),
			status.getReasonPhrase(),
			code,
			message,
			request.getRequestURI(),
			details
		);
		return ResponseEntity.status(status).body(body);
	}
}
