package com.example.notify.config;

import java.util.function.Supplier;

public final class DataSourceRoleContext {

    private static final ThreadLocal<DataSourceRole> CURRENT = new ThreadLocal<>();

    private DataSourceRoleContext() {
    }

    public static DataSourceRole current() {
        DataSourceRole role = CURRENT.get();
        return role == null ? DataSourceRole.WRITE : role;
    }

    public static void read(Runnable action) {
        runWith(DataSourceRole.READ, action);
    }

    public static <T> T read(Supplier<T> action) {
        return callWith(DataSourceRole.READ, action);
    }

    public static void write(Runnable action) {
        runWith(DataSourceRole.WRITE, action);
    }

    public static <T> T write(Supplier<T> action) {
        return callWith(DataSourceRole.WRITE, action);
    }

    private static void runWith(DataSourceRole role, Runnable action) {
        callWith(role, () -> {
            action.run();
            return null;
        });
    }

    private static <T> T callWith(DataSourceRole role, Supplier<T> action) {
        DataSourceRole previous = CURRENT.get();
        CURRENT.set(role);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

}
