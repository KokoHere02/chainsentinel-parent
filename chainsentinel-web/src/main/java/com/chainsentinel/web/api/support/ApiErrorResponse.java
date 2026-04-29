package com.chainsentinel.web.api.support;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(
	Instant timestamp,
	int status,
	String error,
	String code,
	String message,
	String path,
	List<String> details,
	String traceId
) {

	public static ApiErrorResponse of(
		int status,
		String error,
		String code,
		String message,
		String path,
		List<String> details,
		String traceId
	) {
		return new ApiErrorResponse(
			Instant.now(),
			status,
			error,
			code,
			message,
			path,
			details == null ? List.of() : details,
			traceId
		);
	}
}
