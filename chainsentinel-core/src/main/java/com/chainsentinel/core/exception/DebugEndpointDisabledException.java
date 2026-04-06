package com.chainsentinel.core.exception;

public class DebugEndpointDisabledException extends AppException {

	public DebugEndpointDisabledException() {
		super("DEBUG_ENDPOINT_DISABLED", 404, "Debug endpoint is disabled");
	}
}
