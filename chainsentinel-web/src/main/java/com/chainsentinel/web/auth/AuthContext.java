package com.chainsentinel.web.auth;

public final class AuthContext {

	private static final ThreadLocal<AuthPrincipal> CONTEXT = new ThreadLocal<>();

	private AuthContext() {
	}

	public static void set(AuthPrincipal principal) {
		CONTEXT.set(principal);
	}

	public static AuthPrincipal get() {
		return CONTEXT.get();
	}

	public static void clear() {
		CONTEXT.remove();
	}
}
