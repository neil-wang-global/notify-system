package com.example.notify.interfaces.rest;

import com.example.notify.domain.exception.NotificationExceptionRecord;
import com.example.notify.domain.exception.UserOperationExceptionRecord;
import java.util.List;
import java.util.function.Supplier;

public final class ExceptionsApi {

    private final Supplier<List<UserOperationExceptionRecord>> userOperationExceptions;
    private final Supplier<List<NotificationExceptionRecord>> notificationExceptions;

    public ExceptionsApi(Supplier<List<UserOperationExceptionRecord>> userOperationExceptions, Supplier<List<NotificationExceptionRecord>> notificationExceptions) {
        if (userOperationExceptions == null || notificationExceptions == null) {
            throw new IllegalArgumentException("exception suppliers must not be null");
        }
        this.userOperationExceptions = userOperationExceptions;
        this.notificationExceptions = notificationExceptions;
    }

    public List<UserOperationExceptionRecord> userOperationExceptions() {
        return List.copyOf(userOperationExceptions.get());
    }

    public List<NotificationExceptionRecord> notificationExceptions() {
        return List.copyOf(notificationExceptions.get());
    }

}
