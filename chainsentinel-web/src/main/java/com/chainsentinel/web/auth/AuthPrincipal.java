package com.chainsentinel.web.auth;

import java.util.Set;

public record AuthPrincipal(Long userId, String username, Set<AuthRole> roles) {
}
