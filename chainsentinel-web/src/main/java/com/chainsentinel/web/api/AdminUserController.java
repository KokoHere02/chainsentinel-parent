package com.chainsentinel.web.api;

import com.chainsentinel.web.api.support.RequestTraceFilter;
import com.chainsentinel.web.api.support.ratelimit.RateLimit;
import com.chainsentinel.web.auth.AdminUserService;
import com.chainsentinel.web.auth.AuthRole;
import com.chainsentinel.web.auth.RequireRoles;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Set;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@Validated
@RequireRoles(AuthRole.ADMIN)
public class AdminUserController {

	private final AdminUserService adminUserService;

	public AdminUserController(AdminUserService adminUserService) {
		this.adminUserService = adminUserService;
	}

	@PostMapping
	@RateLimit(name = "admin.user.create", permits = 20, windowSeconds = 60, scope = RateLimit.Scope.IP, message = "Too many user create requests")
	public AdminUserService.UserView createUser(
		@RequestBody @Valid CreateUserRequest request,
		HttpServletRequest httpRequest
	) {
		return adminUserService.createUser(
			request.username(),
			request.password(),
			request.roles(),
			request.enabled(),
			httpRequest,
			traceId(httpRequest)
		);
	}

	@GetMapping
	@RateLimit(name = "admin.user.list", permits = 60, windowSeconds = 60, scope = RateLimit.Scope.IP, message = "Too many user list requests")
	public List<AdminUserService.UserView> listUsers() {
		return adminUserService.listUsers();
	}

	@PatchMapping("/{id}/password")
	@RateLimit(name = "admin.user.password", permits = 30, windowSeconds = 60, scope = RateLimit.Scope.IP, message = "Too many password reset requests")
	public void updatePassword(
		@PathVariable("id") Long userId,
		@RequestBody @Valid UpdatePasswordRequest request,
		HttpServletRequest httpRequest
	) {
		adminUserService.updatePassword(userId, request.password(), httpRequest, traceId(httpRequest));
	}

	@PatchMapping("/{id}/roles")
	@RateLimit(name = "admin.user.roles", permits = 30, windowSeconds = 60, scope = RateLimit.Scope.IP, message = "Too many role update requests")
	public AdminUserService.UserView updateRoles(
		@PathVariable("id") Long userId,
		@RequestBody @Valid UpdateRolesRequest request,
		HttpServletRequest httpRequest
	) {
		return adminUserService.updateRoles(userId, request.roles(), httpRequest, traceId(httpRequest));
	}

	@PatchMapping("/{id}/status")
	@RateLimit(name = "admin.user.status", permits = 30, windowSeconds = 60, scope = RateLimit.Scope.IP, message = "Too many status update requests")
	public AdminUserService.UserView updateStatus(
		@PathVariable("id") Long userId,
		@RequestBody @Valid UpdateStatusRequest request,
		HttpServletRequest httpRequest
	) {
		return adminUserService.updateStatus(userId, request.enabled(), httpRequest, traceId(httpRequest));
	}

	private String traceId(HttpServletRequest request) {
		Object traceValue = request.getAttribute(RequestTraceFilter.REQUEST_ATTR_REQUEST_ID);
		return traceValue == null ? "-" : String.valueOf(traceValue);
	}

	public record CreateUserRequest(
		@NotBlank String username,
		@NotBlank String password,
		@NotEmpty Set<AuthRole> roles,
		Boolean enabled
	) {
		public CreateUserRequest {
			if (enabled == null) {
				enabled = true;
			}
		}
	}

	public record UpdatePasswordRequest(@NotBlank String password) {
	}

	public record UpdateRolesRequest(@NotEmpty Set<AuthRole> roles) {
	}

	public record UpdateStatusRequest(@NotNull Boolean enabled) {
	}
}
