package com.apexcal.domain.task;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public record TaskOccurrence(LocalDate date, TaskItem task, int startMinute, int endMinute) {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public TaskOccurrence {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(task, "task");
        if (startMinute < 0 || endMinute <= startMinute) {
            throw new IllegalArgumentException("任务时间范围不合法");
        }
    }

    public int durationMinutes() {
        return endMinute - startMinute;
    }

    public String formattedTimeRange() {
        return task.startTime().format(TIME_FORMATTER) + " - " + task.endTime().format(TIME_FORMATTER);
    }
}