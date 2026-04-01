package com.chainsentinel.web.api.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chainsentinel.core.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders
      .standaloneSetup(new TestController())
      .setControllerAdvice(new GlobalExceptionHandler())
      .build();
  }

  @Test
  void shouldHandleIllegalArgumentAsBadRequest() throws Exception {
    mockMvc.perform(get("/test/bad-request"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"))
      .andExpect(jsonPath("$.message").value("bad input"))
      .andExpect(jsonPath("$.path").value("/test/bad-request"));
  }

  @Test
  void shouldHandleAppException() throws Exception {
    mockMvc.perform(get("/test/not-found"))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.code").value("NOT_FOUND"))
      .andExpect(jsonPath("$.message").value("rule not found"));
  }

  @Test
  void shouldHandleUnknownExceptionAsInternalError() throws Exception {
    mockMvc.perform(get("/test/crash"))
      .andExpect(status().isInternalServerError())
      .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
      .andExpect(jsonPath("$.message").value("Internal server error"));
  }

  @RestController
  @RequestMapping("/test")
  static class TestController {

    @GetMapping("/bad-request")
    public String badRequest() {
      throw new IllegalArgumentException("bad input");
    }

    @GetMapping("/not-found")
    public String notFound() {
      throw new NotFoundException("rule not found");
    }

    @GetMapping("/crash")
    public String crash() {
      throw new RuntimeException("boom");
    }
  }
}