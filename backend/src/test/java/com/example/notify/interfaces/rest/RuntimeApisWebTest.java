package com.example.notify.interfaces.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class RuntimeApisWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exposesStatusEndpoint() throws Exception {
        mockMvc.perform(get("/api/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.kafka").exists())
            .andExpect(jsonPath("$.redis").exists())
            .andExpect(jsonPath("$.degradationStatus").exists());
    }

    @Test
    void exposesEventSimulationEndpointAndDelegatesProcessing() throws Exception {
        mockMvc.perform(post("/api/events/simulate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "eventId": "event-web-1",
                      "customerId": "customer-1",
                      "userId": "user-1",
                      "eventType": "PRODUCT_VIEW",
                      "fields": { "productId": "P001" }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.eventId").value("event-web-1"));

        mockMvc.perform(get("/api/notifications"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].event.eventId.value").value("event-web-1"));
    }

    @Test
    void exposesNotificationAndExceptionQueryEndpoints() throws Exception {
        mockMvc.perform(get("/api/notifications"))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/exceptions/user-operations"))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/exceptions/notifications"))
            .andExpect(status().isOk());
    }

    @Test
    void exposesStrategySaveEndpoint() throws Exception {
        mockMvc.perform(post("/api/strategies")
                .contentType(MediaType.APPLICATION_JSON)
                .content(strategyRequest("strategy-web-1", "idem-web-1", "Product view strategy", 0)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.strategyId").value("strategy-web-1"));
    }

    @Test
    void strategySaveRejectsMissingRequiredDomainFields() throws Exception {
        assertBadStrategyRequest("""
            {
              "strategyId": "strategy-web-missing",
              "name": "Missing rule fields",
              "userToken": "token-1",
              "idempotencyKey": "idem-web-missing"
            }
            """);
        assertBadStrategyRequest(strategyRequest("", "idem-web-missing-id", "Missing id", 0));
        assertBadStrategyRequest(strategyRequest("strategy-web-missing-name", "idem-web-missing-name", "", 0));
        assertBadStrategyRequest(strategyRequest("strategy-web-missing-token", "idem-web-missing-token", "Missing token", 0).replace("\"userToken\": \"token-1\"", "\"userToken\": \"\""));
        assertBadStrategyRequest(strategyRequest("strategy-web-missing-idem", "", "Missing idempotency", 0));
    }

    @Test
    void strategySaveRejectsEmptyBodyAsBadRequest() throws Exception {
        mockMvc.perform(post("/api/strategies")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void strategyUpdateRejectsPathAndBodyIdMismatch() throws Exception {
        mockMvc.perform(put("/api/strategies/strategy-path")
                .contentType(MediaType.APPLICATION_JSON)
                .content(strategyRequest("strategy-body", "idem-web-mismatch", "Mismatched strategy", 1)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void exposesStrategyUpdateEndpoint() throws Exception {
        mockMvc.perform(post("/api/strategies")
                .contentType(MediaType.APPLICATION_JSON)
                .content(strategyRequest("strategy-web-update", "idem-web-update-1", "Product view strategy", 0)))
            .andExpect(status().isOk());

        mockMvc.perform(put("/api/strategies/strategy-web-update")
                .contentType(MediaType.APPLICATION_JSON)
                .content(strategyRequest("strategy-web-update", "idem-web-update-2", "Product view strategy updated", 1)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.strategyId").value("strategy-web-update"))
            .andExpect(jsonPath("$.version").value(2));
    }

    private void assertBadStrategyRequest(String json) throws Exception {
        mockMvc.perform(post("/api/strategies")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isBadRequest());
    }

    private static String strategyRequest(String strategyId, String idempotencyKey, String name, int expectedVersion) {
        return """
            {
              "strategyId": "%s",
              "name": "%s",
              "scope": { "kind": "GLOBAL", "userIds": [], "userGroupIds": [] },
              "eventType": "PRODUCT_VIEW",
              "userToken": "token-1",
              "idempotencyKey": "%s",
              "executionPlan": "plan-web-1",
              "expectedVersion": %d
            }
            """.formatted(strategyId, name, idempotencyKey, expectedVersion);
    }

}
