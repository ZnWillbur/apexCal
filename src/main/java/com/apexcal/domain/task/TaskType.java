package com.apexcal.domain.task;

public enum TaskType {
    COURSE("课程"),
    CUSTOM("自建"),
    DDL("DDL");

    private final String displayName;

    TaskType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}