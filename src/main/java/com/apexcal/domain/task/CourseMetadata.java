package com.apexcal.domain.task;

public record CourseMetadata(String teacher, String classesInfo, int enrollment) {
    public CourseMetadata {
        teacher = teacher == null ? "" : teacher;
        classesInfo = classesInfo == null ? "" : classesInfo;
        enrollment = Math.max(0, enrollment);
    }

    public static CourseMetadata empty() {
        return new CourseMetadata("", "", 0);
    }
}