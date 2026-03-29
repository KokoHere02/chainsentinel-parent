package com.chainsentinel.web.api;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chainsentinel.core.service.MonitorTokenService;
import com.chainsentinel.core.service.dto.MonitorTokenUpsertCommand;
import com.chainsentinel.core.service.dto.MonitorTokenView;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TokenController.class)
class TokenControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MonitorTokenService monitorTokenService;

    @Test
    void shouldUpsertTokenAndApplyDefaultEnabled() throws Exception {
        when(monitorTokenService.upsert(any(MonitorTokenUpsertCommand.class)))
                .thenReturn(new MonitorTokenView(1L, "ETH", "0xabc", "LINK", true));

        mockMvc.perform(post("/api/tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  \"chain\": \"ETH\",
                                  \"tokenContract\": \"0xAbC\",
                                  \"symbol\": \"LINK\"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.chain", is("ETH")))
                .andExpect(jsonPath("$.tokenContract", is("0xabc")))
                .andExpect(jsonPath("$.enabled", is(true)));

        ArgumentCaptor<MonitorTokenUpsertCommand> captor = ArgumentCaptor.forClass(MonitorTokenUpsertCommand.class);
        verify(monitorTokenService).upsert(captor.capture());
        MonitorTokenUpsertCommand cmd = captor.getValue();
        Assertions.assertEquals("ETH", cmd.chain());
        Assertions.assertEquals("0xAbC", cmd.tokenContract());
        Assertions.assertEquals("LINK", cmd.symbol());
        Assertions.assertTrue(cmd.enabled());
    }
}
