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
import java.nio.file.Files;
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
        long customCount = scheduleService.listAllTasks().stream().filter(task -> task.type() == TaskType.CUSTOM).count();
        long deadlineCount = scheduleService.listAllTasks().stream().filter(task -> task.type() == TaskType.DDL).count();

        Assertions.assertEquals(LocalDate.of(2026, 3, 2), config.firstMonday());
        Assertions.assertEquals(15, config.sections().size());
        Assertions.assertEquals(2, courseCount);
        Assertions.assertEquals(2, customCount);
        Assertions.assertEquals(2, deadlineCount);
    }

    @Test
    void shouldBuildWeeklyOccurrencesFromUnifiedTaskModel() {
        WeekSchedule weekSchedule = scheduleService.buildWeekSchedule(LocalDate.of(2026, 3, 12), 7);

        Assertions.assertEquals(2, weekSchedule.weekNumber());
        Assertions.assertEquals(1, weekSchedule.occurrencesFor(LocalDate.of(2026, 3, 9)).size());
        Assertions.assertEquals(0, weekSchedule.occurrencesFor(LocalDate.of(2026, 3, 10)).size());
        Assertions.assertEquals(1, weekSchedule.occurrencesFor(LocalDate.of(2026, 3, 11)).size());
        Assertions.assertEquals(0, weekSchedule.occurrencesFor(LocalDate.of(2026, 3, 12)).size());
        Assertions.assertEquals(0, weekSchedule.occurrencesFor(LocalDate.of(2026, 3, 13)).size());
    }

    @Test
    void shouldBuildTodaySummary() {
        TodaySummary summary = scheduleService.buildTodaySummary(LocalDate.of(2026, 3, 11));

        Assertions.assertEquals("课程：今天有 1 门", summary.courseSummary());
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

        Assertions.assertEquals(1, weekSchedule.occurrencesFor(LocalDate.of(2026, 3, 12)).size());
        Assertions.assertEquals(1, weekSchedule.deadlinesFor(LocalDate.of(2026, 3, 12)).size());
        Assertions.assertTrue(scheduleService.buildMonthTaskCounts(YearMonth.of(2026, 3)).get(LocalDate.of(2026, 3, 12)) >= 2);
        Assertions.assertTrue(scheduleService.buildYearTaskCounts(2026).get(YearMonth.of(2026, 3)) > 0);
    }

        @Test
        void shouldPreviewAndResolveTemplateImportConflicts() throws Exception {
                Path importDirectory = tempDir.resolve("template-import");
                Files.createDirectories(importDirectory);

                Files.writeString(importDirectory.resolve("class.json"), """
                                {
                                    "semester_start_monday": "2026-09-07",
                                    "courses": [
                                        {
                                            "name": "软件工程",
                                            "weekday": "星期一",
                                            "start_section": 1,
                                            "end_section": 2,
                                            "weeks": "1-16",
                                            "location": "教学楼 A-301",
                                            "teacher": "李老师",
                                            "classes": "24计科1班",
                                            "enrollment": 45
                                        }
                                    ],
                                    "custom_tasks": [
                                        {
                                            "title": "社团例会",
                                            "date": "2026-03-03",
                                            "start_time": "18:30",
                                            "end_time": "19:30",
                                            "location": "活动中心 201",
                                            "note": "提前 10 分钟到场",
                                            "priority": 3,
                                            "color": "#5A8DEE"
                                        }
                                    ],
                                    "ddl_tasks": [
                                        {
                                            "title": "软件工程实验报告",
                                            "due_at": "2026-03-04 23:59",
                                            "location": "课程平台",
                                            "note": "包含 UML 与测试截图",
                                            "priority": 5,
                                            "color": "#D97706"
                                        }
                                    ]
                                }
                                """);

                Files.writeString(importDirectory.resolve("time.json"), """
                                {
                                    "sections": [
                                        { "section": 1, "start": "08:00", "end": "08:40" },
                                        { "section": 2, "start": "08:45", "end": "09:25" }
                                    ]
                                }
                                """);

                ScheduleService.TemplateImportPreview preview = scheduleService.previewTemplateImport(importDirectory);
                Assertions.assertEquals(3, preview.incomingCount());
                Assertions.assertEquals(3, preview.conflictCount());

                ScheduleService.TemplateImportResult skipResult = scheduleService.importTemplates(importDirectory, ScheduleService.ImportConflictPolicy.SKIP);
                Assertions.assertEquals(0, skipResult.importedCount());
                Assertions.assertEquals(0, skipResult.overwrittenCount());
                Assertions.assertEquals(3, skipResult.skippedCount());
                Assertions.assertEquals(LocalDate.of(2026, 9, 7), scheduleService.getSemesterConfig().firstMonday());

                ScheduleService.TemplateImportResult overwriteResult = scheduleService.importTemplates(importDirectory, ScheduleService.ImportConflictPolicy.OVERWRITE);
                Assertions.assertEquals(0, overwriteResult.importedCount());
                Assertions.assertEquals(3, overwriteResult.overwrittenCount());
                Assertions.assertEquals(0, overwriteResult.skippedCount());
        }
}