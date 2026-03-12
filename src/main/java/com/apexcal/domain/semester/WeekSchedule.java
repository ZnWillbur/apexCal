package com.apexcal.domain.semester;

import com.apexcal.domain.task.TaskItem;
import com.apexcal.domain.task.TaskOccurrence;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record WeekSchedule(
        int weekNumber,
        LocalDate weekStart,
        List<LocalDate> visibleDates,
        Map<LocalDate, List<TaskOccurrence>> timedOccurrencesByDate,
        Map<LocalDate, List<TaskItem>> deadlinesByDate) {

    public WeekSchedule {
        visibleDates = List.copyOf(visibleDates);
        timedOccurrencesByDate = Map.copyOf(timedOccurrencesByDate);
        deadlinesByDate = Map.copyOf(deadlinesByDate);
    }

    public List<TaskOccurrence> occurrencesFor(LocalDate date) {
        return timedOccurrencesByDate.getOrDefault(date, List.of());
    }

    public List<TaskItem> deadlinesFor(LocalDate date) {
        return deadlinesByDate.getOrDefault(date, List.of());
    }

    public int ddlCountFor(LocalDate date) {
        return deadlinesFor(date).size();
    }

    public int totalTaskCountFor(LocalDate date) {
        return occurrencesFor(date).size() + deadlinesFor(date).size();
    }
}
