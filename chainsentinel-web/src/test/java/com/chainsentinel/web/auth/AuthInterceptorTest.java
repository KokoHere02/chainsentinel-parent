package com.chainsentinel.web.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chainsentinel.web.api.OrderController;
import com.chainsentinel.web.api.support.GlobalExceptionHandler;
import com.chainsentinel.web.auth.audit.AuditEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthInterceptorTest {

	private MockMvc mockMvc;
	private JwtTokenService jwtTokenService;

	@BeforeEach
	void setUp() {
		AuthProperties properties = new AuthProperties();
		properties.setJwtSecret("test-secret");
		properties.setAccessTokenTtlSeconds(3600);
		jwtTokenService = new JwtTokenService(new ObjectMapper(), properties);
		AuditEventPublisher publisher = new AuditEventPublisher(event -> {});
		AuthInterceptor authInterceptor = new AuthInterceptor(jwtTokenService, publisher);
		mockMvc = MockMvcBuilders.standaloneSetup(new OrderController(publisher))
			.setControllerAdvice(new GlobalExceptionHandler())
			.addInterceptors(authInterceptor)
			.build();
	}

	@Test
	void shouldReturnUnauthorizedWhenMissingToken() throws Exception {
		mockMvc.perform(post("/api/orders")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "symbol": "BTCUSDT",
					  "side": "BUY",
					  "quantity": 1
					}
					"""))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_TOKEN_MISSING"));
	}

	@Test
	void shouldReturnUnauthorizedWhenTokenInvalid() throws Exception {
		mockMvc.perform(post("/api/orders")
				.header("Authorization", "Bearer bad-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "symbol": "BTCUSDT",
					  "side": "BUY",
					  "quantity": 1
					}
					"""))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"));
	}

	@Test
	void shouldReturnForbiddenWhenRoleInsufficient() throws Exception {
		String token = jwtTokenService.issueToken(new AuthPrincipal(2L, "operator", Set.of(AuthRole.OPERATOR)));
		mockMvc.perform(post("/api/orders")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "symbol": "BTCUSDT",
					  "side": "BUY",
					  "quantity": 1
					}
					"""))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("AUTH_ROLE_FORBIDDEN"));
	}

	@Test
	void shouldPassWhenRoleAllowed() throws Exception {
		String token = jwtTokenService.issueToken(new AuthPrincipal(3L, "trader", Set.of(AuthRole.TRADER)));
		mockMvc.perform(post("/api/orders")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "symbol": "BTCUSDT",
					  "side": "BUY",
					  "quantity": 1
					}
					"""))
			.andExpect(status().isOk());
	}
}
