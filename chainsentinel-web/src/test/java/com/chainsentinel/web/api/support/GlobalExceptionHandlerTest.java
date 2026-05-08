package com.chainsentinel.web.api.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chainsentinel.core.exception.NotFoundException;
import com.chainsentinel.core.exception.CoreErrorCode;
import com.chainsentinel.core.exception.TradeRiskException;
import com.chainsentinel.web.auth.AuthContext;
import com.chainsentinel.web.auth.AuthPrincipal;
import com.chainsentinel.web.auth.AuthRole;
import com.chainsentinel.web.auth.audit.AuditEvent;
import com.chainsentinel.web.auth.audit.AuditEventPublisher;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

  @Mock
  private AuditEventPublisher auditEventPublisher;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders
      .standaloneSetup(new TestController(), new OrderController())
      .setControllerAdvice(new GlobalExceptionHandler(auditEventPublisher))
      .build();
  }

  @Test
  void shouldHandleIllegalArgumentAsBadRequest() throws Exception {
    mockMvc.perform(get("/test/bad-request"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"))
      .andExpect(jsonPath("$.message").value("bad input"))
      .andExpect(jsonPath("$.path").value("/test/bad-request"))
      .andExpect(jsonPath("$.traceId").value("-"));
  }

  @Test
  void shouldHandleAppException() throws Exception {
    mockMvc.perform(get("/test/not-found"))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.code").value("NOT_FOUND"))
      .andExpect(jsonPath("$.message").value("rule not found"))
      .andExpect(jsonPath("$.traceId").value("-"));
  }

  @Test
  void shouldHandleUnknownExceptionAsInternalError() throws Exception {
    mockMvc.perform(get("/test/crash"))
      .andExpect(status().isInternalServerError())
      .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
      .andExpect(jsonPath("$.message").value("Internal server error"))
      .andExpect(jsonPath("$.traceId").value("-"));
  }

  @Test
  void shouldPublishStableAuditReasonForOrderCreateFailure() throws Exception {
    try {
      AuthContext.set(new AuthPrincipal(7L, "admin", Set.of(AuthRole.ADMIN)));

      mockMvc.perform(post("/api/orders"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("TRADE_DISABLED"));

      ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
      verify(auditEventPublisher).publish(captor.capture());
      org.junit.jupiter.api.Assertions.assertEquals("ORDER_CREATE_FAIL", captor.getValue().action());
      org.junit.jupiter.api.Assertions.assertEquals(7L, captor.getValue().userId());
      org.junit.jupiter.api.Assertions.assertEquals("admin", captor.getValue().username());
      org.junit.jupiter.api.Assertions.assertEquals(
        "code=TRADE_DISABLED,message=trade is disabled",
        captor.getValue().reason()
      );
    } finally {
      AuthContext.clear();
    }
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

  @RestController
  @RequestMapping("/api/orders")
  static class OrderController {

    @PostMapping
    public String createOrder() {
      throw new TradeRiskException(CoreErrorCode.TRADE_DISABLED, "trade is disabled");
    }
  }
}
