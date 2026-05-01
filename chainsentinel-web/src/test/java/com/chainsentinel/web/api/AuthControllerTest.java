package com.chainsentinel.web.api;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chainsentinel.web.api.support.GlobalExceptionHandler;
import com.chainsentinel.web.auth.AuthErrorCode;
import com.chainsentinel.web.auth.AuthException;
import com.chainsentinel.web.auth.AuthRole;
import com.chainsentinel.web.auth.AuthService;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

	@Mock
	private AuthService authService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		AuthController controller = new AuthController(authService);
		mockMvc = MockMvcBuilders.standaloneSetup(controller)
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();
	}

	@Test
	void shouldLoginAndReturnTokens() throws Exception {
		when(authService.login(eq("admin"), eq("admin123"), any(), eq("-")))
			.thenReturn(new AuthService.LoginResult(
				"access-token",
				"refresh-token",
				"Bearer",
				1L,
				"admin",
				Set.of(AuthRole.ADMIN)
			));

		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "username": "admin",
					  "password": "admin123"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessToken", is("access-token")))
			.andExpect(jsonPath("$.refreshToken", is("refresh-token")))
			.andExpect(jsonPath("$.tokenType", is("Bearer")))
			.andExpect(jsonPath("$.username", is("admin")));
	}

	@Test
	void shouldRefreshAndReturnRotatedTokens() throws Exception {
		when(authService.refresh(eq("r1"), any(), eq("-")))
			.thenReturn(new AuthService.LoginResult(
				"access-2",
				"refresh-2",
				"Bearer",
				1L,
				"admin",
				Set.of(AuthRole.ADMIN)
			));

		mockMvc.perform(post("/api/auth/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "refreshToken": "r1"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessToken", is("access-2")))
			.andExpect(jsonPath("$.refreshToken", is("refresh-2")));
	}

	@Test
	void shouldLogoutAndReturnNoContent() throws Exception {
		mockMvc.perform(post("/api/auth/logout")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "refreshToken": "r1"
					}
					"""))
			.andExpect(status().isNoContent());

		verify(authService).logout(eq("r1"), any(), eq("-"));
	}

	@Test
	void shouldReturnBadRequestWhenLoginBodyInvalid() throws Exception {
		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "username": "",
					  "password": ""
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code", is("VALIDATION_ERROR")));
	}

	@Test
	void shouldReturnAuthErrorCodeWhenServiceThrowsAuthException() throws Exception {
		doThrow(new AuthException(AuthErrorCode.AUTH_LOGIN_LOCKED, HttpStatus.TOO_MANY_REQUESTS, "locked"))
			.when(authService).login(eq("admin"), eq("admin123"), any(), eq("-"));

		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "username": "admin",
					  "password": "admin123"
					}
					"""))
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code", is("AUTH_LOGIN_LOCKED")));
	}
}
