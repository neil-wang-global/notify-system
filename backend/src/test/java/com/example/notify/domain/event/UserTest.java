package com.example.notify.domain.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class UserTest {

    @Test
    void userTokenTrimsAndConvertsToString() {
        assertEquals("token-1", new UserToken(" token-1 ").toString());
    }

    @Test
    void userHasGroups() {
        User user = new User(new UserId("user-1"), List.of(new UserGroupId("group-1"), new UserGroupId("group-2")));

        assertEquals(new UserId("user-1"), user.userId());
        assertEquals(List.of(new UserGroupId("group-1"), new UserGroupId("group-2")), user.groupIds());
    }

    @Test
    void rejectsBlankToken() {
        assertThrows(IllegalArgumentException.class, () -> new UserToken(" "));
    }

}
