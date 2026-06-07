package com.example.notify.domain.strategy;

import com.example.notify.domain.event.UserGroupId;
import com.example.notify.domain.event.UserId;
import java.util.Arrays;
import java.util.List;

public record StrategyScope(Kind kind, List<UserId> userIds, List<UserGroupId> userGroupIds) {

    public enum Kind {
        GLOBAL,
        USERS,
        USER_GROUPS
    }

    public StrategyScope {
        if (kind == null) {
            throw new IllegalArgumentException("strategy scope kind must not be null");
        }
        if (userIds == null || userGroupIds == null) {
            throw new IllegalArgumentException("strategy scope ids must not be null");
        }
        userIds = List.copyOf(userIds);
        userGroupIds = List.copyOf(userGroupIds);
        if (kind == Kind.GLOBAL && (!userIds.isEmpty() || !userGroupIds.isEmpty())) {
            throw new IllegalArgumentException("global strategy scope must not include users or groups");
        }
        if (kind == Kind.USERS && (userIds.isEmpty() || !userGroupIds.isEmpty())) {
            throw new IllegalArgumentException("users strategy scope must include only users");
        }
        if (kind == Kind.USER_GROUPS && (!userIds.isEmpty() || userGroupIds.isEmpty())) {
            throw new IllegalArgumentException("user groups strategy scope must include only groups");
        }
    }

    public static StrategyScope global() {
        return new StrategyScope(Kind.GLOBAL, List.of(), List.of());
    }

    public static StrategyScope users(UserId... userIds) {
        if (userIds == null || userIds.length == 0) {
            throw new IllegalArgumentException("strategy users scope must contain users");
        }
        return new StrategyScope(Kind.USERS, Arrays.asList(userIds), List.of());
    }

    public static StrategyScope userGroups(UserGroupId... userGroupIds) {
        if (userGroupIds == null || userGroupIds.length == 0) {
            throw new IllegalArgumentException("strategy user groups scope must contain groups");
        }
        return new StrategyScope(Kind.USER_GROUPS, List.of(), Arrays.asList(userGroupIds));
    }

}
