package com.apexcal.domain.task;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public record TaskItem(
        String uuid,
        TaskType type,
        TaskSource source,
        String title,
        String note,
        String location,
        String colorHex,
        String metadataJson,
        TaskStatus status,
        int priority,
        DayOfWeek weekday,
        Integer startMinute,
        Integer endMinute,
        String weekPattern,
        LocalDate startDate,
        LocalDate endDate,
        LocalDateTime dueAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        int version) {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public TaskItem {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");

        title = title.trim();
        note = note == null ? "" : note.trim();
        location = location == null ? "" : location.trim();
        colorHex = colorHex == null || colorHex.isBlank() ? "#C84C4C" : colorHex.trim();
        metadataJson = metadataJson == null || metadataJson.isBlank() ? "{}" : metadataJson;
        weekPattern = weekPattern == null ? "" : weekPattern.trim();

        if (title.isEmpty()) {
            throw new IllegalArgumentException("任务标题不能为空");
        }
        if (priority < 1 || priority > 5) {
            throw new IllegalArgumentException("任务优先级必须在 1 到 5 之间");
        }
        if (type == TaskType.DDL) {
            if (dueAt == null) {
                throw new IllegalArgumentException("DDL 必须包含截止时间");
            }
        } else {
            if (weekday == null || startMinute == null || endMinute == null) {
                throw new IllegalArgumentException("时间块任务必须包含星期与起止时间");
            }
            if (startMinute < 0 || endMinute > 1440 || endMinute <= startMinute) {
                throw new IllegalArgumentException("任务起止时间不合法");
            }
            if (type == TaskType.COURSE && weekPattern.isBlank()) {
                throw new IllegalArgumentException("课程任务必须包含周次范围");
            }
            if (type == TaskType.CUSTOM && startDate == null) {
                throw new IllegalArgumentException("自建任务必须包含日期");
            }
        }
    }

    public boolean isDeadline() {
        return type == TaskType.DDL;
    }

    public boolean isCourse() {
        return type == TaskType.COURSE;
    }

    public boolean isCustom() {
        return type == TaskType.CUSTOM;
    }

    public LocalTime startTime() {
        if (startMinute == null) {
            return null;
        }
        return LocalTime.of(startMinute / 60, startMinute % 60);
    }

    public LocalTime endTime() {
        if (endMinute == null) {
            return null;
        }
        return LocalTime.of(endMinute / 60, endMinute % 60);
    }

    public String formattedTimeRange() {
        if (startMinute == null || endMinute == null) {
            return "";
        }
        return startTime().format(TIME_FORMATTER) + " - " + endTime().format(TIME_FORMATTER);
    }
}