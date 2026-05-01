package com.chainsentinel.web.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class JwtTokenServiceTest {

	@Test
	void shouldIssueAndParseToken() {
		AuthProperties properties = new AuthProperties();
		properties.setJwtSecret("test-secret");
		properties.setAccessTokenTtlSeconds(3600);
		JwtTokenService service = new JwtTokenService(new ObjectMapper(), properties);

		AuthPrincipal principal = new AuthPrincipal(1L, "admin", Set.of(AuthRole.ADMIN, AuthRole.TRADER));
		String token = service.issueToken(principal);
		AuthPrincipal parsed = service.verifyAndParse(token);

		Assertions.assertEquals(1L, parsed.userId());
		Assertions.assertEquals("admin", parsed.username());
		Assertions.assertTrue(parsed.roles().contains(AuthRole.ADMIN));
		Assertions.assertTrue(parsed.roles().contains(AuthRole.TRADER));
	}
}
