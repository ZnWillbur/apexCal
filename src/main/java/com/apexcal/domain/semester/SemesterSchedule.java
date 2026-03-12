package com.apexcal.domain.semester;

import com.apexcal.domain.layout.TimeSection;
import com.apexcal.domain.task.CourseTask;
import java.time.LocalDate;
import java.util.List;

public record SemesterSchedule(LocalDate firstMonday, List<TimeSection> sections, List<CourseTask> courses) {
    public SemesterSchedule {
        sections = List.copyOf(sections);
        courses = List.copyOf(courses);
    }
}
