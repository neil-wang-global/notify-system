package com.example.notify.interfaces.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class StrategiesGetWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listReturnsEmptyWhenNoStrategies() throws Exception {
        mockMvc.perform(get("/api/strategies"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    void listReturnsCreatedStrategies() throws Exception {
        mockMvc.perform(post("/api/strategies")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "strategyId": "strategy-list-1",
                      "name": "List test strategy",
                      "scope": { "kind": "GLOBAL", "userIds": [], "userGroupIds": [] },
                      "eventType": "PRODUCT_VIEW",
                      "userToken": "token-1",
                      "threshold": 1,
                      "idempotencyKey": "idem-list-1"
                    }
                    """))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/strategies"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.strategyId == 'strategy-list-1')]").exists());
    }

    @Test
    void getByIdReturnsStrategy() throws Exception {
        mockMvc.perform(post("/api/strategies")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "strategyId": "strategy-get-1",
                      "name": "Get test strategy",
                      "scope": { "kind": "GLOBAL", "userIds": [], "userGroupIds": [] },
                      "eventType": "PRODUCT_VIEW",
                      "userToken": "token-1",
                      "threshold": 1,
                      "idempotencyKey": "idem-get-1"
                    }
                    """))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/strategies/strategy-get-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.strategyId").value("strategy-get-1"))
            .andExpect(jsonPath("$.name").value("Get test strategy"))
            .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void getByIdReturns400ForUnknownStrategy() throws Exception {
        mockMvc.perform(get("/api/strategies/nonexistent"))
            .andExpect(status().isBadRequest());
    }
}
