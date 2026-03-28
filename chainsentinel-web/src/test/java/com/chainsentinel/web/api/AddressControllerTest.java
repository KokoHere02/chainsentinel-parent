package com.chainsentinel.web.api;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chainsentinel.core.service.MonitorAddressService;
import com.chainsentinel.core.service.dto.MonitorAddressUpsertCommand;
import com.chainsentinel.core.service.dto.MonitorAddressView;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AddressController.class)
class AddressControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MonitorAddressService monitorAddressService;

    @Test
    void shouldUpsertAddressAndApplyDefaultEnabled() throws Exception {
        when(monitorAddressService.upsert(any(MonitorAddressUpsertCommand.class)))
                .thenReturn(new MonitorAddressView(1L, "ETH", "0xabc", "vip", true));

        mockMvc.perform(post("/api/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "chain": "ETH",
                                  "address": "0xAbC",
                                  "tag": "vip"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.chain", is("ETH")))
                .andExpect(jsonPath("$.address", is("0xabc")))
                .andExpect(jsonPath("$.enabled", is(true)));

        ArgumentCaptor<MonitorAddressUpsertCommand> captor = ArgumentCaptor.forClass(MonitorAddressUpsertCommand.class);
        verify(monitorAddressService).upsert(captor.capture());
        MonitorAddressUpsertCommand cmd = captor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals("ETH", cmd.chain());
        org.junit.jupiter.api.Assertions.assertEquals("0xAbC", cmd.address());
        org.junit.jupiter.api.Assertions.assertEquals("vip", cmd.tag());
        org.junit.jupiter.api.Assertions.assertEquals(true, cmd.enabled());
    }

    @Test
    void shouldReturn400WhenChainIsBlank() throws Exception {
        mockMvc.perform(post("/api/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "chain": "",
                                  "address": "0xabc"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }
}
