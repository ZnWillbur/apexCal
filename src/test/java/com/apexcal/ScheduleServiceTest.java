package com.apexcal;

import com.apexcal.application.service.ScheduleService;
import com.apexcal.application.service.TodaySummary;
import com.apexcal.domain.semester.SemesterConfig;
import com.apexcal.domain.semester.WeekSchedule;
import com.apexcal.domain.task.TaskDraft;
import com.apexcal.domain.task.TaskType;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.nio.file.Path;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScheduleServiceTest {
    @TempDir
    Path tempDir;

    private ScheduleService scheduleService;

    @BeforeEach
    void setUp() {
        scheduleService = new ScheduleService(tempDir);
    }

    @Test
    void shouldLoadImportedCourseDataIntoDatabase() {
        SemesterConfig config = scheduleService.getSemesterConfig();
        long courseCount = scheduleService.listAllTasks().stream().filter(task -> task.type() == TaskType.COURSE).count();

        Assertions.assertEquals(LocalDate.of(2026, 3, 2), config.firstMonday());
        Assertions.assertEquals(15, config.sections().size());
        Assertions.assertEquals(16, courseCount);
    }

    @Test
    void shouldBuildWeeklyOccurrencesFromUnifiedTaskModel() {
        WeekSchedule weekSchedule = scheduleService.buildWeekSchedule(LocalDate.of(2026, 3, 12), 7);

        Assertions.assertEquals(2, weekSchedule.weekNumber());
        Assertions.assertEquals(1, weekSchedule.occurrencesFor(LocalDate.of(2026, 3, 9)).size());
        Assertions.assertEquals(4, weekSchedule.occurrencesFor(LocalDate.of(2026, 3, 10)).size());
        Assertions.assertEquals(4, weekSchedule.occurrencesFor(LocalDate.of(2026, 3, 11)).size());
        Assertions.assertEquals(2, weekSchedule.occurrencesFor(LocalDate.of(2026, 3, 12)).size());
        Assertions.assertEquals(1, weekSchedule.occurrencesFor(LocalDate.of(2026, 3, 13)).size());
    }

    @Test
    void shouldBuildTodaySummary() {
        TodaySummary summary = scheduleService.buildTodaySummary(LocalDate.of(2026, 3, 12));

        Assertions.assertEquals("课程：今天有 2 门", summary.courseSummary());
        Assertions.assertEquals("自建：0 项", summary.customSummary());
        Assertions.assertEquals("DDL：0 项", summary.deadlineSummary());
    }

    @Test
    void shouldPersistCustomTaskAndDeadlineIntoCalendarCounts() {
        scheduleService.createTask(new TaskDraft(
                TaskType.CUSTOM,
                "准备汇报",
                "整理演示材料",
                "图书馆",
                null,
                4,
                null,
                "",
                LocalDate.of(2026, 3, 12),
                LocalTime.of(19, 0),
                LocalTime.of(20, 30),
                null,
                null));
        scheduleService.createTask(new TaskDraft(
                TaskType.DDL,
                "数据库实验提交",
                "上传报告",
                "雨课堂",
                null,
                5,
                null,
                "",
                null,
                null,
                null,
                LocalDate.of(2026, 3, 12),
                LocalTime.of(23, 59)));

        WeekSchedule weekSchedule = scheduleService.buildWeekSchedule(LocalDate.of(2026, 3, 12), 7);

        Assertions.assertEquals(3, weekSchedule.occurrencesFor(LocalDate.of(2026, 3, 12)).size());
        Assertions.assertEquals(1, weekSchedule.deadlinesFor(LocalDate.of(2026, 3, 12)).size());
        Assertions.assertTrue(scheduleService.buildMonthTaskCounts(YearMonth.of(2026, 3)).get(LocalDate.of(2026, 3, 12)) >= 4);
        Assertions.assertTrue(scheduleService.buildYearTaskCounts(2026).get(YearMonth.of(2026, 3)) > 0);
    }
}