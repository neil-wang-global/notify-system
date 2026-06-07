package com.example.notify.domain.exception;

import java.util.Optional;

public interface UserOperationExceptions {

    void add(UserOperationExceptionRecord record);

    Optional<UserOperationExceptionRecord> find(String id);

}
