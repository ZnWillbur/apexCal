package com.apexcal.domain.task;

import java.time.DayOfWeek;
import java.util.Objects;

public record CourseTask(
        String name,
        DayOfWeek weekday,
        int startSection,
        int endSection,
        WeekPattern weekPattern,
        String location,
        String teacher,
        String classesInfo,
        int enrollment) {

    public CourseTask {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(weekday, "weekday");
        Objects.requireNonNull(weekPattern, "weekPattern");
        if (startSection <= 0 || endSection < startSection) {
            throw new IllegalArgumentException("Invalid section range");
        }
        location = location == null || location.isBlank() ? "地点待定" : location;
        teacher = Objects.requireNonNullElse(teacher, "");
        classesInfo = Objects.requireNonNullElse(classesInfo, "");
    }

    public boolean occursInWeek(int weekNumber) {
        return weekPattern.contains(weekNumber);
    }

    public int sectionSpan() {
        return endSection - startSection + 1;
    }
}
