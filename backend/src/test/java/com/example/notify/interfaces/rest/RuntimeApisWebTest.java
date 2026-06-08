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
        // First create a strategy so the candidate index has something to match
        mockMvc.perform(post("/api/strategies")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "strategyId": "strategy-event-test",
                      "name": "Event test strategy",
                      "scope": { "kind": "GLOBAL", "userIds": [], "userGroupIds": [] },
                      "eventType": "PRODUCT_VIEW",
                      "userToken": "token-1",
                      "idempotencyKey": "idem-event-test"
                    }
                    """))
            .andExpect(status().isOk());

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

    @Test
    void strategySaveWithRowBasedRulesCreatesStrategy() throws Exception {
        mockMvc.perform(post("/api/strategies")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "strategyId": "strategy-web-rows",
                      "name": "Row Based Strategy",
                      "scope": { "kind": "GLOBAL", "userIds": [], "userGroupIds": [] },
                      "rules": [
                        { "field": "eventType", "operator": "EQ", "value": "PRODUCT_VIEW", "connector": "AND", "group": "root", "sortOrder": 1 },
                        { "field": "productId", "operator": "IN", "value": ["P001", "P002"], "connector": "AND", "group": "root", "sortOrder": 2 }
                      ],
                      "executionPlan": "plan-web-rows",
                      "userToken": "token-1",
                      "idempotencyKey": "idem-web-rows"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.strategyId").value("strategy-web-rows"));
    }

    @Test
    void strategySaveWithUsersScopeAndRowRulesReturns200AndVersion1() throws Exception {
        mockMvc.perform(post("/api/strategies")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "strategyId": "strategy-users-1",
                      "name": "Users scoped strategy",
                      "scope": { "kind": "USERS", "userIds": ["user-1", "user-2"], "userGroupIds": [] },
                      "rules": [
                        { "field": "eventType", "operator": "EQ", "value": "PRODUCT_VIEW", "connector": "AND", "group": "root", "sortOrder": 1 }
                      ],
                      "executionPlan": "plan-users-1",
                      "userToken": "token-1",
                      "idempotencyKey": "idem-users-1"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.strategyId").value("strategy-users-1"))
            .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void strategySaveWithUserGroupsScopeReturns200AndVersion1() throws Exception {
        mockMvc.perform(post("/api/strategies")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "strategyId": "strategy-groups-1",
                      "name": "User groups scoped strategy",
                      "scope": { "kind": "USER_GROUPS", "userIds": [], "userGroupIds": ["group-1", "group-2"] },
                      "rules": [
                        { "field": "eventType", "operator": "EQ", "value": "ORDER_CREATED", "connector": "AND", "group": "root", "sortOrder": 1 }
                      ],
                      "executionPlan": "plan-groups-1",
                      "userToken": "token-1",
                      "idempotencyKey": "idem-groups-1"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.strategyId").value("strategy-groups-1"))
            .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void strategyUpdateWithCorrectVersionBumpsToVersion2() throws Exception {
        mockMvc.perform(post("/api/strategies")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "strategyId": "strategy-update-v2",
                      "name": "Version 1",
                      "scope": { "kind": "USERS", "userIds": ["user-1"], "userGroupIds": [] },
                      "rules": [
                        { "field": "eventType", "operator": "EQ", "value": "PRODUCT_VIEW", "connector": "AND", "group": "root", "sortOrder": 1 }
                      ],
                      "executionPlan": "plan-update-v2",
                      "userToken": "token-1",
                      "idempotencyKey": "idem-update-v2-1"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(put("/api/strategies/strategy-update-v2")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "strategyId": "strategy-update-v2",
                      "name": "Version 2",
                      "scope": { "kind": "USERS", "userIds": ["user-1", "user-2"], "userGroupIds": [] },
                      "rules": [
                        { "field": "eventType", "operator": "EQ", "value": "ORDER_CREATED", "connector": "AND", "group": "root", "sortOrder": 1 }
                      ],
                      "executionPlan": "plan-update-v2",
                      "expectedVersion": 1,
                      "userToken": "token-1",
                      "idempotencyKey": "idem-update-v2-2"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.strategyId").value("strategy-update-v2"))
            .andExpect(jsonPath("$.version").value(2));
    }

    @Test
    void strategyUpdateWithStaleVersionReturns400() throws Exception {
        mockMvc.perform(post("/api/strategies")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "strategyId": "strategy-stale",
                      "name": "Stale test",
                      "scope": { "kind": "GLOBAL", "userIds": [], "userGroupIds": [] },
                      "eventType": "PRODUCT_VIEW",
                      "executionPlan": "plan-stale",
                      "userToken": "token-1",
                      "idempotencyKey": "idem-stale-1"
                    }
                    """))
            .andExpect(status().isOk());

        mockMvc.perform(put("/api/strategies/strategy-stale")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "strategyId": "strategy-stale",
                      "name": "Stale update",
                      "scope": { "kind": "GLOBAL", "userIds": [], "userGroupIds": [] },
                      "eventType": "PRODUCT_VIEW",
                      "executionPlan": "plan-stale",
                      "expectedVersion": 0,
                      "userToken": "token-1",
                      "idempotencyKey": "idem-stale-2"
                    }
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void idempotencyKeyReturnsSameStrategyIdOnIdenticalRequest() throws Exception {
        String requestJson = """
            {
              "strategyId": "strategy-idem",
              "name": "Idempotency test",
              "scope": { "kind": "USERS", "userIds": ["user-1"], "userGroupIds": [] },
              "rules": [
                { "field": "eventType", "operator": "EQ", "value": "PRODUCT_VIEW", "connector": "AND", "group": "root", "sortOrder": 1 }
              ],
              "executionPlan": "plan-idem",
              "userToken": "token-1",
              "idempotencyKey": "idem-key-same"
            }
            """;

        mockMvc.perform(post("/api/strategies")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.strategyId").value("strategy-idem"))
            .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(post("/api/strategies")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.strategyId").value("strategy-idem"))
            .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void idempotencyKeyWithDifferentPayloadReturns400() throws Exception {
        mockMvc.perform(post("/api/strategies")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "strategyId": "strategy-idem-diff",
                      "name": "Original name",
                      "scope": { "kind": "GLOBAL", "userIds": [], "userGroupIds": [] },
                      "eventType": "PRODUCT_VIEW",
                      "executionPlan": "plan-idem-diff",
                      "userToken": "token-1",
                      "idempotencyKey": "idem-key-diff"
                    }
                    """))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/strategies")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "strategyId": "strategy-idem-diff",
                      "name": "Changed name",
                      "scope": { "kind": "GLOBAL", "userIds": [], "userGroupIds": [] },
                      "eventType": "PRODUCT_VIEW",
                      "executionPlan": "plan-idem-diff",
                      "userToken": "token-1",
                      "idempotencyKey": "idem-key-diff"
                    }
                    """))
            .andExpect(status().isBadRequest());
    }

}
