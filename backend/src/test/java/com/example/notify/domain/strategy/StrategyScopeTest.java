package com.example.notify.domain.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.notify.domain.event.UserGroupId;
import com.example.notify.domain.event.UserId;
import java.util.List;
import org.junit.jupiter.api.Test;

class StrategyScopeTest {

    @Test
    void supportsGlobalScope() {
        StrategyScope scope = StrategyScope.global();

        assertEquals(StrategyScope.Kind.GLOBAL, scope.kind());
        assertEquals(List.of(), scope.userIds());
        assertEquals(List.of(), scope.userGroupIds());
    }

    @Test
    void supportsUsersScope() {
        StrategyScope scope = StrategyScope.users(new UserId("user-1"), new UserId("user-2"));

        assertEquals(StrategyScope.Kind.USERS, scope.kind());
        assertEquals(List.of(new UserId("user-1"), new UserId("user-2")), scope.userIds());
    }

    @Test
    void supportsUserGroupsScope() {
        StrategyScope scope = StrategyScope.userGroups(new UserGroupId("group-1"));

        assertEquals(StrategyScope.Kind.USER_GROUPS, scope.kind());
        assertEquals(List.of(new UserGroupId("group-1")), scope.userGroupIds());
    }

    @Test
    void rejectsEmptyUsersScope() {
        assertThrows(IllegalArgumentException.class, StrategyScope::users);
    }

    @Test
    void rejectsEmptyUserGroupsScope() {
        assertThrows(IllegalArgumentException.class, StrategyScope::userGroups);
    }

    @Test
    void rejectsGlobalScopeWithUsers() {
        assertThrows(IllegalArgumentException.class, () -> new StrategyScope(
            StrategyScope.Kind.GLOBAL,
            List.of(new UserId("user-1")),
            List.of()
        ));
    }

    @Test
    void rejectsUsersScopeWithoutUsers() {
        assertThrows(IllegalArgumentException.class, () -> new StrategyScope(
            StrategyScope.Kind.USERS,
            List.of(),
            List.of()
        ));
    }

    @Test
    void rejectsUserGroupsScopeWithoutGroups() {
        assertThrows(IllegalArgumentException.class, () -> new StrategyScope(
            StrategyScope.Kind.USER_GROUPS,
            List.of(),
            List.of()
        ));
    }

}
