package com.apexcal.domain.layout;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public record TimeSection(int section, LocalTime start, LocalTime end) {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public String formattedRange() {
        return start.format(FORMATTER) + " - " + end.format(FORMATTER);
    }

    public int startMinute() {
        return start.getHour() * 60 + start.getMinute();
    }

    public int endMinute() {
        return end.getHour() * 60 + end.getMinute();
    }
}
