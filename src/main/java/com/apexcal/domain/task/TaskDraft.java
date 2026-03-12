package com.apexcal.domain.task;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

public record TaskDraft(
        TaskType type,
        String title,
        String note,
        String location,
        String colorHex,
        int priority,
        DayOfWeek weekday,
        String weekPattern,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        LocalDate dueDate,
        LocalTime dueTime) {
}