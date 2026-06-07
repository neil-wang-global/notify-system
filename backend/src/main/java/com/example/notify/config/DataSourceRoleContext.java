package com.example.notify.config;

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

    public static void write(Runnable action) {
        runWith(DataSourceRole.WRITE, action);
    }

    private static void runWith(DataSourceRole role, Runnable action) {
        DataSourceRole previous = CURRENT.get();
        CURRENT.set(role);
        try {
            action.run();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

}
