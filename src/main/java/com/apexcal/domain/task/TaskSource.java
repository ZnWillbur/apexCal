package com.apexcal.domain.task;

public enum TaskSource {
    USER,
    IMPORTED_CLASS_JSON;

    public static TaskSource fromPersistence(String raw) {
        if (raw == null || raw.isBlank()) {
            return USER;
        }
        return TaskSource.valueOf(raw);
    }
}