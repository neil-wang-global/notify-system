package com.example.notify.domain.event;

import java.util.List;

public record User(UserId userId, List<UserGroupId> groupIds) {

    public User {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (groupIds == null) {
            throw new IllegalArgumentException("userGroupIds must not be null");
        }
        groupIds = List.copyOf(groupIds);
    }

}
