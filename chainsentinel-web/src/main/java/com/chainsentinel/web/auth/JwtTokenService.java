package com.chainsentinel.web.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenService {

	private static final TypeReference<Map<String, Object>> MAP_REF = new TypeReference<>() {
	};

	private final ObjectMapper objectMapper;
	private final AuthProperties authProperties;

	public JwtTokenService(ObjectMapper objectMapper, AuthProperties authProperties) {
		this.objectMapper = objectMapper;
		this.authProperties = authProperties;
		validateSecret(authProperties);
	}

	public String issueToken(AuthPrincipal principal) {
		Instant now = Instant.now();
		Instant exp = now.plusSeconds(authProperties.getAccessTokenTtlSeconds());
		Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
		Map<String, Object> payload = new HashMap<>();
		payload.put("sub", String.valueOf(principal.userId()));
		payload.put("uid", principal.userId());
		payload.put("username", principal.username());
		payload.put("roles", principal.roles().stream().map(Enum::name).toList());
		payload.put("iat", now.getEpochSecond());
		payload.put("exp", exp.getEpochSecond());
		try {
			String encodedHeader = base64Url(objectMapper.writeValueAsBytes(header));
			String encodedPayload = base64Url(objectMapper.writeValueAsBytes(payload));
			String content = encodedHeader + "." + encodedPayload;
			String signature = sign(content);
			return content + "." + signature;
		} catch (Exception ex) {
			throw new IllegalStateException("Failed to issue jwt token", ex);
		}
	}

	public AuthPrincipal verifyAndParse(String token) {
		if (token == null || token.isBlank()) {
			throw new IllegalArgumentException("Missing token");
		}
		String[] parts = token.split("\\.");
		if (parts.length != 3) {
			throw new IllegalArgumentException("Invalid token format");
		}
		String content = parts[0] + "." + parts[1];
		if (!sign(content).equals(parts[2])) {
			throw new IllegalArgumentException("Invalid token signature");
		}
		try {
			byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
			Map<String, Object> payload = objectMapper.readValue(payloadBytes, MAP_REF);
			long exp = toLong(payload.get("exp"));
			if (Instant.now().getEpochSecond() >= exp) {
				throw new IllegalArgumentException("Token expired");
			}
			Long uid = toLong(payload.get("uid"));
			String username = String.valueOf(payload.get("username"));
			List<String> roleTexts = objectMapper.convertValue(payload.get("roles"), new TypeReference<List<String>>() {
			});
			Set<AuthRole> roles = new HashSet<>();
			for (String roleText : roleTexts) {
				roles.add(AuthRole.valueOf(roleText));
			}
			return new AuthPrincipal(uid, username, roles);
		} catch (IllegalArgumentException ex) {
			throw ex;
		} catch (Exception ex) {
			throw new IllegalArgumentException("Invalid token payload", ex);
		}
	}

	private String sign(String content) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(authProperties.getJwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			return base64Url(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception ex) {
			throw new IllegalStateException("Failed to sign token", ex);
		}
	}

	private String base64Url(byte[] value) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
	}

	private long toLong(Object raw) {
		if (raw instanceof Number number) {
			return number.longValue();
		}
		return Long.parseLong(String.valueOf(raw));
	}

	private void validateSecret(AuthProperties properties) {
		String secret = properties.getJwtSecret();
		if (secret == null || secret.isBlank() || AuthProperties.DEFAULT_JWT_SECRET.equals(secret)) {
			throw new IllegalStateException(
				"chainsentinel.auth.jwt-secret must be configured explicitly and must not use the default placeholder"
			);
		}
	}
}
