package com.example.notify.engine.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.notify.domain.event.EventType;
import com.example.notify.domain.event.UserGroupId;
import com.example.notify.domain.event.UserId;
import com.example.notify.domain.strategy.StrategyId;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CandidateStrategyIndexTest {

    @Test
    void combinesScopeEventTypeAndFieldIndexes() {
        CandidateStrategyIndex index = new CandidateStrategyIndex();
        index.addGlobal(new StrategyId("global-view"));
        index.addUser(new UserId("user-1"), new StrategyId("user-view"));
        index.addGroup(new UserGroupId("group-1"), new StrategyId("group-view"));
        index.addEventType(new EventType("PRODUCT_VIEW"), new StrategyId("global-view"), new StrategyId("user-view"), new StrategyId("group-view"), new StrategyId("other-event"));
        index.addField("productId", "P001", new StrategyId("global-view"), new StrategyId("group-view"));

        Set<StrategyId> candidates = index.candidates(new EventSnapshot(
            "customer-1",
            "user-1",
            Set.of("group-1"),
            "PRODUCT_VIEW",
            Map.of("productId", "P001")
        ));

        assertEquals(Set.of(new StrategyId("global-view"), new StrategyId("group-view")), candidates);
    }

    @Test
    void doesNotScanStrategiesWhenEventIndexMisses() {
        CandidateStrategyIndex index = new CandidateStrategyIndex();
        index.addGlobal(new StrategyId("global-view"));

        Set<StrategyId> candidates = index.candidates(new EventSnapshot(
            "customer-1",
            "user-1",
            Set.of(),
            "PRODUCT_VIEW",
            Map.of()
        ));

        assertEquals(Set.of(), candidates);
    }

}
