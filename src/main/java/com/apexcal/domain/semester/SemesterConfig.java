package com.apexcal.domain.semester;

import com.apexcal.domain.layout.TimeSection;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record SemesterConfig(
        String semesterName,
        LocalDate firstMonday,
        int totalWeeks,
        int weekViewDays,
        List<TimeSection> sections) {

    public SemesterConfig {
        semesterName = semesterName == null || semesterName.isBlank() ? "默认学期" : semesterName.trim();
        Objects.requireNonNull(firstMonday, "firstMonday");
        if (totalWeeks <= 0) {
            throw new IllegalArgumentException("学期总周数必须大于 0");
        }
        if (weekViewDays != 5 && weekViewDays != 7) {
            throw new IllegalArgumentException("周视图只支持 5 天或 7 天");
        }
        sections = List.copyOf(sections);
    }
}