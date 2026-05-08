package com.chainsentinel.web.api.support;

import com.chainsentinel.core.exception.AppException;
import com.chainsentinel.web.auth.AuthContext;
import com.chainsentinel.web.auth.AuthException;
import com.chainsentinel.web.auth.AuthPrincipal;
import com.chainsentinel.web.auth.audit.AuditEvent;
import com.chainsentinel.web.auth.audit.AuditEventPublisher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
	private final AuditEventPublisher auditEventPublisher;

	public GlobalExceptionHandler() {
		this.auditEventPublisher = null;
	}

	@Autowired
	public GlobalExceptionHandler(AuditEventPublisher auditEventPublisher) {
		this.auditEventPublisher = auditEventPublisher;
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(
		MethodArgumentNotValidException ex,
		HttpServletRequest request
	) {
		List<String> details = ex.getBindingResult().getFieldErrors().stream()
			.map(this::toFieldError)
			.collect(Collectors.toList());
		logHandled(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_ERROR, "Request validation failed", request);
		return build(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_ERROR, "Request validation failed", request, details);
	}

	@ExceptionHandler(BindException.class)
	public ResponseEntity<ApiErrorResponse> handleBindException(
		BindException ex,
		HttpServletRequest request
	) {
		List<String> details = ex.getBindingResult().getFieldErrors().stream()
			.map(this::toFieldError)
			.collect(Collectors.toList());
		logHandled(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_ERROR, "Request binding failed", request);
		return build(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_ERROR, "Request binding failed", request, details);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
		ConstraintViolationException ex,
		HttpServletRequest request
	) {
		List<String> details = ex.getConstraintViolations().stream()
			.map(v -> v.getPropertyPath() + ": " + v.getMessage())
			.toList();
		logHandled(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_ERROR, "Request validation failed", request);
		return build(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_ERROR, "Request validation failed", request, details);
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiErrorResponse> handleMethodArgumentTypeMismatch(
		MethodArgumentTypeMismatchException ex,
		HttpServletRequest request
	) {
		String detail = ex.getName() + ": invalid value " + ex.getValue();
		logHandled(HttpStatus.BAD_REQUEST, ApiErrorCode.TYPE_MISMATCH, "Request parameter type mismatch", request);
		return build(
		HttpStatus.BAD_REQUEST,
		ApiErrorCode.TYPE_MISMATCH,
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
		logHandled(HttpStatus.BAD_REQUEST, ApiErrorCode.INVALID_REQUEST_BODY, "Request body is invalid or unreadable", request);
		return build(
		HttpStatus.BAD_REQUEST,
		ApiErrorCode.INVALID_REQUEST_BODY,
		"Request body is invalid or unreadable",
		request,
		List.of()
		);
	}

	@ExceptionHandler(AppException.class)
	public ResponseEntity<ApiErrorResponse> handleAppException(AppException ex, HttpServletRequest request) {
		HttpStatus status = HttpStatus.valueOf(ex.getStatus());
		logHandled(status, ex.getCode(), ex.getMessage(), request);
		return build(status, ex.getCode(), ex.getMessage(), request, List.of());
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
		IllegalArgumentException ex,
		HttpServletRequest request
	) {
		logHandled(HttpStatus.BAD_REQUEST, ApiErrorCode.INVALID_ARGUMENT, ex.getMessage(), request);
		return build(HttpStatus.BAD_REQUEST, ApiErrorCode.INVALID_ARGUMENT, ex.getMessage(), request, List.of());
	}

	@ExceptionHandler(AuthException.class)
	public ResponseEntity<ApiErrorResponse> handleAuthException(AuthException ex, HttpServletRequest request) {
		HttpStatus status = ex.getStatus();
		String code = ex.getErrorCode().name();
		String message = ex.getMessage();
		logHandled(status, code, message, request);
		return build(status, code, message, request, List.of());
	}

	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<ApiErrorResponse> handleResponseStatus(
		ResponseStatusException ex,
		HttpServletRequest request
	) {
		HttpStatusCode statusCode = ex.getStatusCode();
		HttpStatus status = HttpStatus.valueOf(statusCode.value());
		String message = ex.getReason() == null ? status.getReasonPhrase() : ex.getReason();
		logHandled(status, ApiErrorCode.HTTP_ERROR, message, request);
		return build(status, ApiErrorCode.HTTP_ERROR, message, request, List.of());
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiErrorResponse> handleUnhandled(Exception ex, HttpServletRequest request) {
		String requestId = readRequestId(request);
		log.error(
			"Unhandled exception requestId={} method={} path={} message={}",
			requestId,
			request.getMethod(),
			request.getRequestURI(),
			LogSanitizer.sanitizeMessage(ex.getMessage()),
			ex
		);
		return build(HttpStatus.INTERNAL_SERVER_ERROR, ApiErrorCode.INTERNAL_ERROR, "Internal server error", request, List.of());
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
		publishOrderCreateFailIfNeeded(status, code, message, request);
		String requestId = readRequestId(request);
		ApiErrorResponse body = ApiErrorResponse.of(
			status.value(),
			status.getReasonPhrase(),
			code,
			message,
			request.getRequestURI(),
			details,
			requestId
		);
		return ResponseEntity.status(status).body(body);
	}

	private ResponseEntity<ApiErrorResponse> build(
		HttpStatus status,
		ApiErrorCode code,
		String message,
		HttpServletRequest request,
		List<String> details
	) {
		return build(status, code.value(), message, request, details);
	}

	private void logHandled(
		HttpStatus status,
		String code,
		String message,
		HttpServletRequest request
	) {
		log.warn(
			"api.error traceId={} status={} code={} method={} path={} message={}",
			readRequestId(request),
			status.value(),
			code,
			request.getMethod(),
			request.getRequestURI(),
			LogSanitizer.sanitizeMessage(message)
		);
	}

	private void logHandled(
		HttpStatus status,
		ApiErrorCode code,
		String message,
		HttpServletRequest request
	) {
		logHandled(status, code.value(), message, request);
	}

	private String readRequestId(HttpServletRequest request) {
		Object value = request.getAttribute(RequestTraceFilter.REQUEST_ATTR_REQUEST_ID);
		return value == null ? "-" : String.valueOf(value);
	}

	private void publishOrderCreateFailIfNeeded(HttpStatus status, String code, String message, HttpServletRequest request) {
		if (auditEventPublisher == null) {
			return;
		}
		if (!status.isError()) {
			return;
		}
		if (!"POST".equalsIgnoreCase(request.getMethod())) {
			return;
		}
		if (!"/api/orders".equals(request.getRequestURI())) {
			return;
		}
		String traceId = readRequestId(request);
		AuthPrincipal principal = AuthContext.get();
		auditEventPublisher.publish(new AuditEvent(
			"ORDER_CREATE_FAIL",
			principal == null ? null : principal.userId(),
			principal == null ? null : principal.username(),
			"FAIL",
			buildAuditReason(code, message),
			traceId,
			request.getRemoteAddr(),
			request.getRequestURI(),
			request.getMethod()
		));
	}

	private String buildAuditReason(String code, String message) {
		String sanitizedCode = LogSanitizer.sanitizeMessage(code);
		String sanitizedMessage = LogSanitizer.sanitizeMessage(message);
		if (sanitizedCode == null || sanitizedCode.isBlank()) {
			return sanitizedMessage;
		}
		if (sanitizedMessage == null || sanitizedMessage.isBlank()) {
			return "code=" + sanitizedCode;
		}
		return "code=" + sanitizedCode + ",message=" + sanitizedMessage;
	}
}
