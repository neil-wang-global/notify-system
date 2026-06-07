package com.example.notify.domain.exception;

import java.util.Optional;

public interface NotificationExceptions {

    void add(NotificationExceptionRecord record);

    Optional<NotificationExceptionRecord> find(String id);

}
