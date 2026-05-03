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
import com.chainsentinel.web.auth.AdminUserService;
import com.chainsentinel.web.auth.AuthRole;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AdminUserControllerTest {

	@Mock
	private AdminUserService adminUserService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new AdminUserController(adminUserService))
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();
	}

	@Test
	void shouldCreateUser() throws Exception {
		when(adminUserService.createUser(eq("alice"), eq("password"), eq(Set.of(AuthRole.OPERATOR)), eq(true), any(), eq("-")))
			.thenReturn(new AdminUserService.UserView(2L, "alice", true, Set.of(AuthRole.OPERATOR)));

		mockMvc.perform(post("/api/admin/users")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "username": "alice",
					  "password": "password",
					  "roles": ["OPERATOR"]
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id", is(2)))
			.andExpect(jsonPath("$.username", is("alice")))
			.andExpect(jsonPath("$.roles", hasSize(1)));
	}

	@Test
	void shouldListUsers() throws Exception {
		when(adminUserService.listUsers(0, 100)).thenReturn(List.of(
			new AdminUserService.UserView(1L, "admin", true, Set.of(AuthRole.ADMIN)),
			new AdminUserService.UserView(2L, "alice", false, Set.of(AuthRole.TRADER))
		));

		mockMvc.perform(get("/api/admin/users"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[1].username", is("alice")))
			.andExpect(jsonPath("$[1].enabled", is(false)));
	}

	@Test
	void shouldPassRawUserPageArgumentsToService() throws Exception {
		when(adminUserService.listUsers(-3, 999)).thenReturn(List.of());

		mockMvc.perform(get("/api/admin/users")
				.param("page", "-3")
				.param("size", "999"))
			.andExpect(status().isOk());

		verify(adminUserService).listUsers(-3, 999);
	}

	@Test
	void shouldUpdatePassword() throws Exception {
		mockMvc.perform(patch("/api/admin/users/2/password")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "password": "new-password"
					}
					"""))
			.andExpect(status().isOk());

		verify(adminUserService).updatePassword(eq(2L), eq("new-password"), any(), eq("-"));
	}

	@Test
	void shouldUpdateRoles() throws Exception {
		when(adminUserService.updateRoles(eq(2L), eq(Set.of(AuthRole.TRADER)), any(), eq("-")))
			.thenReturn(new AdminUserService.UserView(2L, "alice", true, Set.of(AuthRole.TRADER)));

		mockMvc.perform(patch("/api/admin/users/2/roles")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "roles": ["TRADER"]
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.roles[0]", is("TRADER")));
	}

	@Test
	void shouldUpdateStatus() throws Exception {
		when(adminUserService.updateStatus(eq(2L), eq(false), any(), eq("-")))
			.thenReturn(new AdminUserService.UserView(2L, "alice", false, Set.of(AuthRole.OPERATOR)));

		mockMvc.perform(patch("/api/admin/users/2/status")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "enabled": false
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.enabled", is(false)));
	}
}
