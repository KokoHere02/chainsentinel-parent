package com.chainsentinel.web.api;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AuthSessionControllerTest {

	@Mock
	private AuthService authService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		AuthSessionController controller = new AuthSessionController(authService);
		mockMvc = MockMvcBuilders.standaloneSetup(controller)
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();
	}

	@AfterEach
	void tearDown() {
		AuthContext.clear();
	}

	@Test
	void shouldListSessions() throws Exception {
		AuthContext.set(new AuthPrincipal(1L, "admin", Set.of(AuthRole.ADMIN)));
		when(authService.listActiveSessions(1L)).thenReturn(List.of(
			new AuthService.SessionView("t1****0001", "t10001", "127.0.0.1", "ua1", Instant.now(), Instant.now().plusSeconds(3600)),
			new AuthService.SessionView("t2****0002", "t20002", "127.0.0.1", "ua2", Instant.now(), Instant.now().plusSeconds(7200))
		));

		mockMvc.perform(get("/api/auth/sessions"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[0].tokenId", is("t1****0001")))
			.andExpect(jsonPath("$[0].revokeTokenId", is("t10001")));
	}

	@Test
	void shouldRevokeOneSession() throws Exception {
		AuthContext.set(new AuthPrincipal(1L, "admin", Set.of(AuthRole.ADMIN)));

		mockMvc.perform(delete("/api/auth/sessions/t1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.revokedCount", is(1)));

		verify(authService).revokeSession(eq(1L), eq("t1"), any(), eq("-"));
	}

	@Test
	void shouldRevokeAllSessions() throws Exception {
		AuthContext.set(new AuthPrincipal(1L, "admin", Set.of(AuthRole.ADMIN)));
		when(authService.revokeAllSessions(eq(1L), any(), eq("-"))).thenReturn(3);

		mockMvc.perform(delete("/api/auth/sessions"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.revokedCount", is(3)));
	}

	@Test
	void shouldReturnUnauthorizedWhenNoPrincipal() throws Exception {
		mockMvc.perform(get("/api/auth/sessions"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code", is("AUTH_TOKEN_INVALID")));
	}
}
