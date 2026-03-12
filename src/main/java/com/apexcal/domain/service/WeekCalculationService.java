package com.apexcal.domain.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class WeekCalculationService {
    private static final DateTimeFormatter RANGE_FORMATTER = DateTimeFormatter.ofPattern("M.d");

    public LocalDate weekStart(LocalDate anchorDate) {
        return anchorDate.with(DayOfWeek.MONDAY);
    }

    public List<LocalDate> visibleDates(LocalDate anchorDate, int visibleDays) {
        LocalDate monday = weekStart(anchorDate);
        List<LocalDate> dates = new ArrayList<>(visibleDays);
        for (int index = 0; index < visibleDays; index++) {
            dates.add(monday.plusDays(index));
        }
        return dates;
    }

    public int academicWeekNumber(LocalDate anchorDate, LocalDate firstMonday) {
        long dayDelta = java.time.temporal.ChronoUnit.DAYS.between(firstMonday, weekStart(anchorDate));
        return (int) Math.floorDiv(dayDelta, 7) + 1;
    }

    public String formatWeekLabel(int weekNumber) {
        if (weekNumber < -30 || weekNumber > 30) {
            return "第N周";
        }
        return "第" + weekNumber + "周";
    }

    public String formatRange(List<LocalDate> dates) {
        if (dates.isEmpty()) {
            return "";
        }
        LocalDate start = dates.getFirst();
        LocalDate end = dates.getLast();
        return start.format(RANGE_FORMATTER) + " - " + end.format(RANGE_FORMATTER);
    }
}
