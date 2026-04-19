package com.chainsentinel.web.api.support.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

	String name() default "";

	int permits();

	long windowSeconds() default 1;

	Scope scope() default Scope.GLOBAL;

	String message() default "Too many requests";

	enum Scope {
		GLOBAL,
		IP
	}
}