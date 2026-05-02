package com.chainsentinel.web.api;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chainsentinel.web.api.support.GlobalExceptionHandler;
import com.chainsentinel.web.auth.AuthContext;
import com.chainsentinel.web.auth.AuthPrincipal;
import com.chainsentinel.web.auth.AuthRole;
import com.chainsentinel.web.auth.AuthService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AuthProfileControllerTest {

	@Mock
	private AuthService authService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new AuthProfileController(authService))
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();
		AuthContext.set(new AuthPrincipal(1L, "admin", Set.of(AuthRole.ADMIN)));
	}

	@AfterEach
	void tearDown() {
		AuthContext.clear();
	}

	@Test
	void shouldReturnMe() throws Exception {
		when(authService.me(1L)).thenReturn(new AuthService.MeView(1L, "admin", true, Set.of(AuthRole.ADMIN), 2));

		mockMvc.perform(get("/api/auth/me"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.username", is("admin")))
			.andExpect(jsonPath("$.activeSessionCount", is(2)));
	}

	@Test
	void shouldChangePassword() throws Exception {
		mockMvc.perform(patch("/api/auth/password")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "currentPassword": "old-password",
					  "newPassword": "new-password"
					}
					"""))
			.andExpect(status().isNoContent());

		verify(authService).changePassword(eq(1L), eq("old-password"), eq("new-password"), any(), eq("-"));
	}

	@Test
	void shouldLogoutAll() throws Exception {
		when(authService.revokeAllSessions(eq(1L), any(), eq("-"))).thenReturn(3);

		mockMvc.perform(post("/api/auth/logout-all"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.revokedCount", is(3)));
	}

	@Test
	void shouldReturnMyAuditLogs() throws Exception {
		when(authService.listMyAuditLogs(1L, 50)).thenReturn(List.of(
			new AuthService.AuditLogView("LOGIN_SUCCESS", "SUCCESS", "", "t1", "127.0.0.1", "/api/auth/login", "POST", Instant.now())
		));

		mockMvc.perform(get("/api/auth/audit"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].action", is("LOGIN_SUCCESS")));
	}
}
