package com.apexcal.application.service;

import com.apexcal.domain.layout.TimeSection;
import com.apexcal.domain.semester.SemesterConfig;
import com.apexcal.domain.semester.WeekSchedule;
import com.apexcal.domain.service.WeekCalculationService;
import com.apexcal.domain.task.CourseMetadata;
import com.apexcal.domain.task.CourseTask;
import com.apexcal.domain.task.TaskDraft;
import com.apexcal.domain.task.TaskItem;
import com.apexcal.domain.task.TaskOccurrence;
import com.apexcal.domain.task.TaskSource;
import com.apexcal.domain.task.TaskStatus;
import com.apexcal.domain.task.TaskType;
import com.apexcal.domain.task.WeekPattern;
import com.apexcal.infrastructure.config.AppDirectories;
import com.apexcal.infrastructure.json.ObjectMapperFactory;
import com.apexcal.infrastructure.persistence.ConfigRepository;
import com.apexcal.infrastructure.persistence.DatabaseManager;
import com.apexcal.infrastructure.persistence.SQLiteConfigRepository;
import com.apexcal.infrastructure.persistence.SQLiteTaskRepository;
import com.apexcal.infrastructure.persistence.TaskRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ScheduleService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScheduleService.class);
    private static final java.time.format.DateTimeFormatter FLEXIBLE_DOTTED_DATE = new DateTimeFormatterBuilder()
            .appendValue(ChronoField.YEAR, 4)
            .appendLiteral('.')
            .appendValue(ChronoField.MONTH_OF_YEAR)
            .appendLiteral('.')
            .appendValue(ChronoField.DAY_OF_MONTH)
            .toFormatter(Locale.ROOT);
    private static final java.time.format.DateTimeFormatter FLEXIBLE_DASHED_DATE = new DateTimeFormatterBuilder()
            .appendValue(ChronoField.YEAR, 4)
            .appendLiteral('-')
            .appendValue(ChronoField.MONTH_OF_YEAR)
            .appendLiteral('-')
            .appendValue(ChronoField.DAY_OF_MONTH)
            .toFormatter(Locale.ROOT);
    private static final java.time.format.DateTimeFormatter SHORT_DOTTED_DATE = new DateTimeFormatterBuilder()
            .appendValue(ChronoField.MONTH_OF_YEAR)
            .appendLiteral('.')
            .appendValue(ChronoField.DAY_OF_MONTH)
            .toFormatter(Locale.ROOT);

    private final ObjectMapper objectMapper = ObjectMapperFactory.create();
    private final WeekCalculationService weekCalculationService = new WeekCalculationService();
    private final DatabaseManager databaseManager;
    private final TaskRepository taskRepository;
    private final ConfigRepository configRepository;

        public record MonthOverview(
            Map<LocalDate, Integer> totalCounts,
            Map<LocalDate, Integer> deadlineCounts,
            int totalTasks,
            int maxDailyTasks) {
        }

        public record YearOverview(
            Map<YearMonth, Integer> monthlyCounts,
            int totalTasks,
            int maxMonthlyTasks) {
        }

    public ScheduleService() {
        this(AppDirectories.dataDirectory());
    }

    public ScheduleService(Path dataDirectory) {
        this(new DatabaseManager(dataDirectory));
    }

    public ScheduleService(DatabaseManager databaseManager) {
        this(databaseManager, new SQLiteTaskRepository(databaseManager), new SQLiteConfigRepository(databaseManager));
    }

    public ScheduleService(DatabaseManager databaseManager, TaskRepository taskRepository, ConfigRepository configRepository) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
        this.taskRepository = Objects.requireNonNull(taskRepository, "taskRepository");
        this.configRepository = Objects.requireNonNull(configRepository, "configRepository");
        this.databaseManager.initialize();
        bootstrap();
    }

    public SemesterConfig getSemesterConfig() {
        return configRepository.loadSemesterConfig().orElseThrow(() -> new IllegalStateException("缺少学期配置"));
    }

    public List<TaskItem> listAllTasks() {
        return taskRepository.findAllActive();
    }

    public Optional<TaskItem> findTask(String uuid) {
        return taskRepository.findById(uuid);
    }

    public TaskItem createTask(TaskDraft draft) {
        return taskRepository.save(buildTaskItem(UUID.randomUUID().toString(), draft, TaskSource.USER, "{}", LocalDateTime.now(), 1));
    }

    public TaskItem updateTask(String uuid, TaskDraft draft) {
        TaskItem existing = taskRepository.findById(uuid)
                .orElseThrow(() -> new IllegalArgumentException("未找到要修改的任务"));
        return taskRepository.save(buildTaskItem(
                existing.uuid(),
                draft,
                existing.source(),
                existing.metadataJson(),
                existing.createdAt(),
                existing.version() + 1));
    }

    public void deleteTask(String uuid) {
        taskRepository.softDelete(uuid);
    }

    public WeekSchedule buildWeekSchedule(LocalDate anchorDate, int visibleDays) {
        SemesterConfig config = getSemesterConfig();
        List<LocalDate> visibleDates = weekCalculationService.visibleDates(anchorDate, visibleDays);
        List<TaskItem> tasks = taskRepository.findAllActive();

        Map<LocalDate, List<TaskOccurrence>> occurrencesByDate = new LinkedHashMap<>();
        Map<LocalDate, List<TaskItem>> deadlinesByDate = new LinkedHashMap<>();
        for (LocalDate date : visibleDates) {
            occurrencesByDate.put(date, buildOccurrencesForDate(date, tasks, config));
            deadlinesByDate.put(date, buildDeadlinesForDate(date, tasks));
        }

        return new WeekSchedule(
                weekCalculationService.academicWeekNumber(anchorDate, config.firstMonday()),
                weekCalculationService.weekStart(anchorDate),
                visibleDates,
                occurrencesByDate,
                deadlinesByDate);
    }

    public List<TaskOccurrence> listOccurrencesForDate(LocalDate date) {
        return buildOccurrencesForDate(date, taskRepository.findAllActive(), getSemesterConfig());
    }

    public List<TaskItem> listDeadlinesForDate(LocalDate date) {
        return buildDeadlinesForDate(date, taskRepository.findAllActive());
    }

    public TodaySummary buildTodaySummary(LocalDate today) {
        List<TaskOccurrence> occurrences = listOccurrencesForDate(today);
        List<TaskItem> deadlines = listDeadlinesForDate(today);
        long courseCount = occurrences.stream().filter(occurrence -> occurrence.task().type() == TaskType.COURSE).count();
        long customCount = occurrences.stream().filter(occurrence -> occurrence.task().type() == TaskType.CUSTOM).count();
        return new TodaySummary(
                courseCount == 0 ? "课程：今天没有课程" : "课程：今天有 " + courseCount + " 门",
                customCount == 0 ? "自建：0 项" : "自建：" + customCount + " 项",
                deadlines.isEmpty() ? "DDL：0 项" : "DDL：" + deadlines.size() + " 项");
    }

    public Map<LocalDate, Integer> buildMonthTaskCounts(YearMonth month) {
        return buildMonthOverview(month).totalCounts();
    }

    public Map<YearMonth, Integer> buildYearTaskCounts(int year) {
        return buildYearOverview(year).monthlyCounts();
    }

    public MonthOverview buildMonthOverview(YearMonth month) {
        List<TaskItem> tasks = taskRepository.findAllActive();
        SemesterConfig config = getSemesterConfig();
        return buildMonthOverview(month, tasks, config);
    }

    public YearOverview buildYearOverview(int year) {
        List<TaskItem> tasks = taskRepository.findAllActive();
        SemesterConfig config = getSemesterConfig();

        Map<YearMonth, Integer> monthlyCounts = new LinkedHashMap<>();
        int totalTasks = 0;
        int maxMonthlyTasks = 0;
        for (int monthValue = 1; monthValue <= 12; monthValue++) {
            YearMonth month = YearMonth.of(year, monthValue);
            MonthOverview monthOverview = buildMonthOverview(month, tasks, config);
            int monthlyTotal = monthOverview.totalTasks();
            monthlyCounts.put(month, monthlyTotal);
            totalTasks += monthlyTotal;
            maxMonthlyTasks = Math.max(maxMonthlyTasks, monthlyTotal);
        }
        return new YearOverview(monthlyCounts, totalTasks, maxMonthlyTasks);
    }

    public SemesterConfig updateFirstMonday(LocalDate firstMonday) {
        return updateSemesterConfig(firstMonday, getSemesterConfig().sections());
    }

    public SemesterConfig updateSemesterConfig(LocalDate firstMonday, List<TimeSection> sections) {
        SemesterConfig current = getSemesterConfig();
        SemesterConfig updated = new SemesterConfig(
                current.semesterName(),
                firstMonday,
                current.totalWeeks(),
                current.weekViewDays(),
                sections);
        configRepository.saveSemesterConfig(updated);
        return updated;
    }

    public void reloadExternalCourseData() {
        ImportedSchedule importedSchedule = loadImportedSchedule(true);
        applyImportedSchedule(importedSchedule);
    }

    public void restoreBundledDefaults() {
        ImportedSchedule importedSchedule = loadImportedSchedule(false);
        applyImportedSchedule(importedSchedule);
    }

    private void applyImportedSchedule(ImportedSchedule importedSchedule) {
        configRepository.saveSemesterConfig(importedSchedule.config());
        taskRepository.deleteByTypeAndSource(TaskType.COURSE, TaskSource.IMPORTED_CLASS_JSON);
        taskRepository.deleteByTypeAndSource(TaskType.CUSTOM, TaskSource.IMPORTED_CLASS_JSON);
        taskRepository.deleteByTypeAndSource(TaskType.DDL, TaskSource.IMPORTED_CLASS_JSON);
        for (CourseTask course : importedSchedule.courses()) {
            taskRepository.save(toImportedTask(course, importedSchedule.sectionLookup()));
        }
        for (TemplateCustomTask customTask : importedSchedule.customTasks()) {
            taskRepository.save(toImportedCustomTask(customTask));
        }
        for (TemplateDeadlineTask deadlineTask : importedSchedule.deadlineTasks()) {
            taskRepository.save(toImportedDeadlineTask(deadlineTask));
        }
    }

    public String buildSourceSummary() {
        Map<TaskType, Long> counts = taskRepository.findAllActive().stream()
                .collect(Collectors.groupingBy(TaskItem::type, Collectors.counting()));
        long courseCount = counts.getOrDefault(TaskType.COURSE, 0L);
        long customCount = counts.getOrDefault(TaskType.CUSTOM, 0L);
        long ddlCount = counts.getOrDefault(TaskType.DDL, 0L);
        return "课程 " + courseCount + " · 自建 " + customCount + " · DDL " + ddlCount;
    }

    public CourseMetadata readCourseMetadata(TaskItem task) {
        try {
            JsonNode root = objectMapper.readTree(task.metadataJson());
            return new CourseMetadata(
                    root.path("teacher").asText(""),
                    root.path("classesInfo").asText(""),
                    root.path("enrollment").asInt(0));
        } catch (Exception exception) {
            return CourseMetadata.empty();
        }
    }

    public int totalTaskCountForDate(LocalDate date) {
        return listOccurrencesForDate(date).size() + listDeadlinesForDate(date).size();
    }

    public String defaultColor(TaskType type) {
        return switch (type) {
            case COURSE -> "#B4463C";
            case CUSTOM -> "#C86B2A";
            case DDL -> "#8E2430";
        };
    }

    private void bootstrap() {
        if (configRepository.loadSemesterConfig().isEmpty()) {
            ImportedSchedule importedSchedule = loadImportedSchedule(true);
            configRepository.saveSemesterConfig(importedSchedule.config());
        }
        if (taskRepository.countByTypeAndSource(TaskType.COURSE, TaskSource.IMPORTED_CLASS_JSON) == 0) {
            ImportedSchedule importedSchedule = loadImportedSchedule(true);
            for (CourseTask course : importedSchedule.courses()) {
                taskRepository.save(toImportedTask(course, importedSchedule.sectionLookup()));
            }
            for (TemplateCustomTask customTask : importedSchedule.customTasks()) {
                taskRepository.save(toImportedCustomTask(customTask));
            }
            for (TemplateDeadlineTask deadlineTask : importedSchedule.deadlineTasks()) {
                taskRepository.save(toImportedDeadlineTask(deadlineTask));
            }
        }
    }

    private TaskItem buildTaskItem(
            String uuid,
            TaskDraft draft,
            TaskSource source,
            String metadataJson,
            LocalDateTime createdAt,
            int version) {
        LocalDateTime now = LocalDateTime.now();
        return switch (draft.type()) {
            case COURSE -> new TaskItem(
                    uuid,
                    TaskType.COURSE,
                    source,
                    draft.title(),
                    draft.note(),
                    draft.location(),
                    normalizeColor(draft.colorHex(), TaskType.COURSE),
                    metadataJson,
                    TaskStatus.ACTIVE,
                    draft.priority(),
                    draft.weekday(),
                    toMinute(draft.startTime()),
                    toMinute(draft.endTime()),
                    draft.weekPattern(),
                    null,
                    null,
                    null,
                    createdAt,
                    now,
                    version);
            case CUSTOM -> new TaskItem(
                    uuid,
                    TaskType.CUSTOM,
                    source,
                    draft.title(),
                    draft.note(),
                    draft.location(),
                    normalizeColor(draft.colorHex(), TaskType.CUSTOM),
                    metadataJson,
                    TaskStatus.ACTIVE,
                    draft.priority(),
                    draft.date() == null ? draft.weekday() : draft.date().getDayOfWeek(),
                    toMinute(draft.startTime()),
                    toMinute(draft.endTime()),
                    "",
                    draft.date(),
                    draft.date(),
                    null,
                    createdAt,
                    now,
                    version);
            case DDL -> new TaskItem(
                    uuid,
                    TaskType.DDL,
                    source,
                    draft.title(),
                    draft.note(),
                    draft.location(),
                    normalizeColor(draft.colorHex(), TaskType.DDL),
                    metadataJson,
                    TaskStatus.ACTIVE,
                    draft.priority(),
                    null,
                    null,
                    null,
                    "",
                    null,
                    null,
                    LocalDateTime.of(draft.dueDate(), draft.dueTime()),
                    createdAt,
                    now,
                    version);
        };
    }

    private List<TaskOccurrence> buildOccurrencesForDate(LocalDate date, List<TaskItem> tasks, SemesterConfig config) {
        int weekNumber = weekCalculationService.academicWeekNumber(date, config.firstMonday());
        return tasks.stream()
                .filter(task -> !task.isDeadline())
                .filter(task -> occursOnDate(task, date, weekNumber))
                .map(task -> new TaskOccurrence(date, task, task.startMinute(), task.endMinute()))
                .sorted(Comparator
                        .comparingInt(TaskOccurrence::startMinute)
                        .thenComparing(occurrence -> occurrence.task().title()))
                .toList();
    }

    private List<TaskItem> buildDeadlinesForDate(LocalDate date, List<TaskItem> tasks) {
        return tasks.stream()
                .filter(TaskItem::isDeadline)
                .filter(task -> task.dueAt().toLocalDate().equals(date))
                .sorted(Comparator.comparing(TaskItem::dueAt).thenComparing(TaskItem::title))
                .toList();
    }

    private MonthOverview buildMonthOverview(YearMonth month, List<TaskItem> tasks, SemesterConfig config) {
        Map<LocalDate, Integer> totalCounts = new LinkedHashMap<>();
        Map<LocalDate, Integer> deadlineCounts = new LinkedHashMap<>();

        LocalDate current = month.atDay(1);
        LocalDate end = month.atEndOfMonth();
        int totalTasks = 0;
        int maxDailyTasks = 0;

        while (!current.isAfter(end)) {
            int weekNumber = weekCalculationService.academicWeekNumber(current, config.firstMonday());
            int occurrenceCount = 0;
            int deadlineCount = 0;

            for (TaskItem task : tasks) {
                if (task.isDeadline()) {
                    if (task.dueAt() != null && task.dueAt().toLocalDate().equals(current)) {
                        deadlineCount++;
                    }
                    continue;
                }
                if (occursOnDate(task, current, weekNumber)) {
                    occurrenceCount++;
                }
            }

            int dailyTotal = occurrenceCount + deadlineCount;
            totalCounts.put(current, dailyTotal);
            deadlineCounts.put(current, deadlineCount);
            totalTasks += dailyTotal;
            maxDailyTasks = Math.max(maxDailyTasks, dailyTotal);

            current = current.plusDays(1);
        }

        return new MonthOverview(totalCounts, deadlineCounts, totalTasks, maxDailyTasks);
    }

    private boolean occursOnDate(TaskItem task, LocalDate date, int weekNumber) {
        if (task.type() == TaskType.COURSE) {
            return task.weekday() == date.getDayOfWeek() && WeekPattern.parse(task.weekPattern()).contains(weekNumber);
        }
        if (task.type() == TaskType.CUSTOM) {
            return task.startDate() != null && !date.isBefore(task.startDate()) && !date.isAfter(task.endDate());
        }
        return false;
    }

    private ImportedSchedule loadImportedSchedule(boolean preferExternalTemplate) {
        try {
            List<TimeSection> sections = loadSections(preferExternalTemplate);
            JsonNode classRoot = loadClassRoot(preferExternalTemplate);
            LocalDate firstMonday = parseDate(classRoot.path("semester_start_monday").asText());
            List<CourseTask> courses = new ArrayList<>();
            for (JsonNode courseNode : classRoot.path("courses")) {
                courses.add(new CourseTask(
                        courseNode.path("name").asText(),
                        parseWeekday(courseNode.path("weekday").asText()),
                        courseNode.path("start_section").asInt(),
                        courseNode.path("end_section").asInt(),
                        WeekPattern.parse(courseNode.path("weeks").asText()),
                        courseNode.path("location").asText(),
                        courseNode.path("teacher").asText(),
                        courseNode.path("classes").asText(),
                        courseNode.path("enrollment").asInt()));
            }
            courses.sort(Comparator.comparing(CourseTask::weekday).thenComparingInt(CourseTask::startSection));

            List<TemplateCustomTask> customTasks = new ArrayList<>();
            int customIndex = 1;
            for (JsonNode customNode : classRoot.path("custom_tasks")) {
                customTasks.add(new TemplateCustomTask(
                        valueOrDefault(customNode.path("title").asText(null), "自建任务 " + customIndex),
                        parseDate(customNode.path("date").asText()),
                        parseTime(customNode.path("start_time").asText()),
                        parseTime(customNode.path("end_time").asText()),
                        valueOrDefault(customNode.path("location").asText(null), ""),
                        valueOrDefault(customNode.path("note").asText(null), ""),
                        normalizePriority(customNode.path("priority").asInt(3)),
                        valueOrDefault(customNode.path("color").asText(null), defaultColor(TaskType.CUSTOM))));
                customIndex++;
            }

            List<TemplateDeadlineTask> deadlineTasks = new ArrayList<>();
            int ddlIndex = 1;
            for (JsonNode ddlNode : classRoot.path("ddl_tasks")) {
                deadlineTasks.add(new TemplateDeadlineTask(
                        valueOrDefault(ddlNode.path("title").asText(null), "DDL 任务 " + ddlIndex),
                        parseDateTime(ddlNode.path("due_at").asText()),
                        valueOrDefault(ddlNode.path("location").asText(null), ""),
                        valueOrDefault(ddlNode.path("note").asText(null), ""),
                        normalizePriority(ddlNode.path("priority").asInt(4)),
                        valueOrDefault(ddlNode.path("color").asText(null), defaultColor(TaskType.DDL))));
                ddlIndex++;
            }

            Map<Integer, TimeSection> sectionLookup = sections.stream().collect(Collectors.toMap(TimeSection::section, section -> section));
            SemesterConfig config = new SemesterConfig(
                    buildSemesterName(firstMonday),
                    firstMonday,
                    20,
                    7,
                    sections);
            return new ImportedSchedule(config, courses, sectionLookup, customTasks, deadlineTasks);
        } catch (IOException exception) {
            throw new IllegalStateException("无法加载课程导入数据", exception);
        }
    }

    private TaskItem toImportedTask(CourseTask course, Map<Integer, TimeSection> sectionLookup) {
        TimeSection startSection = sectionLookup.get(course.startSection());
        TimeSection endSection = sectionLookup.get(course.endSection());
        if (startSection == null || endSection == null) {
            throw new IllegalArgumentException("课程节次超出节次模板范围: " + course.name());
        }

        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("teacher", course.teacher());
        metadata.put("classesInfo", course.classesInfo());
        metadata.put("enrollment", course.enrollment());

        LocalDateTime now = LocalDateTime.now();
        return new TaskItem(
                buildImportedCourseUuid(course),
                TaskType.COURSE,
                TaskSource.IMPORTED_CLASS_JSON,
                course.name(),
                "",
                course.location(),
                defaultColor(TaskType.COURSE),
                metadata.toString(),
                TaskStatus.ACTIVE,
                3,
                course.weekday(),
                startSection.startMinute(),
                endSection.endMinute(),
                course.weekPattern().toString(),
                null,
                null,
                null,
                now,
                now,
                1);
    }

    private TaskItem toImportedCustomTask(TemplateCustomTask task) {
        LocalDateTime now = LocalDateTime.now();
        return new TaskItem(
                buildImportedCustomUuid(task),
                TaskType.CUSTOM,
                TaskSource.IMPORTED_CLASS_JSON,
                task.title(),
                task.note(),
                task.location(),
                normalizeColor(task.colorHex(), TaskType.CUSTOM),
                "{}",
                TaskStatus.ACTIVE,
                task.priority(),
                task.date().getDayOfWeek(),
                toMinute(task.startTime()),
                toMinute(task.endTime()),
                "",
                task.date(),
                task.date(),
                null,
                now,
                now,
                1);
    }

    private TaskItem toImportedDeadlineTask(TemplateDeadlineTask task) {
        LocalDateTime now = LocalDateTime.now();
        return new TaskItem(
                buildImportedDeadlineUuid(task),
                TaskType.DDL,
                TaskSource.IMPORTED_CLASS_JSON,
                task.title(),
                task.note(),
                task.location(),
                normalizeColor(task.colorHex(), TaskType.DDL),
                "{}",
                TaskStatus.ACTIVE,
                task.priority(),
                null,
                null,
                null,
                "",
                null,
                null,
                task.dueAt(),
                now,
                now,
                1);
    }

    private JsonNode loadClassRoot(boolean preferExternalTemplate) throws IOException {
        Path classFile = resolveExternalFile("class.json");
        if (preferExternalTemplate && Files.exists(classFile)) {
            return objectMapper.readTree(Files.newBufferedReader(classFile));
        }
        return loadBundledClassData();
    }

    private JsonNode loadBundledClassData() throws IOException {
        try (InputStream inputStream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("config/default-class-data.json")) {
            if (inputStream == null) {
                LOGGER.warn("default-class-data.json not found, falling back to empty course set");
                return objectMapper.createObjectNode()
                        .put("semester_start_monday", LocalDate.now().with(DayOfWeek.MONDAY).toString())
                        .putArray("courses");
            }
            return objectMapper.readTree(inputStream);
        }
    }

    private List<TimeSection> loadSections(boolean preferExternalTemplate) throws IOException {
        Path timeFile = resolveExternalFile("time.json");
        JsonNode timeRoot;
        if (preferExternalTemplate && Files.exists(timeFile)) {
            timeRoot = objectMapper.readTree(Files.newBufferedReader(timeFile));
        } else {
            try (InputStream inputStream = Thread.currentThread()
                    .getContextClassLoader()
                    .getResourceAsStream("config/default-schedule-template.json")) {
                if (inputStream == null) {
                    throw new IllegalStateException("Missing default schedule template resource");
                }
                timeRoot = objectMapper.readTree(inputStream);
            }
        }

        List<TimeSection> sections = new ArrayList<>();
        for (JsonNode sectionNode : timeRoot.path("sections")) {
            sections.add(new TimeSection(
                    sectionNode.path("section").asInt(),
                    LocalTime.parse(sectionNode.path("start").asText()),
                    LocalTime.parse(sectionNode.path("end").asText())));
        }
        sections.sort(Comparator.comparingInt(TimeSection::section));
        return sections;
    }

    private Path resolveExternalFile(String fileName) {
        return Paths.get(System.getProperty("user.dir"))
                .toAbsolutePath()
                .resolve("config")
                .resolve("import")
                .resolve(fileName);
    }

    private LocalDate parseDate(String raw) {
        String normalized = raw == null ? "" : raw.trim();
        if (normalized.isBlank()) {
            return LocalDate.now().with(DayOfWeek.MONDAY);
        }
        try {
            return LocalDate.parse(normalized, FLEXIBLE_DOTTED_DATE);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDate.parse(normalized, FLEXIBLE_DASHED_DATE);
        } catch (DateTimeParseException ignored) {
        }
        try {
            LocalDate shortDate = LocalDate.parse(normalized, SHORT_DOTTED_DATE);
            return shortDate.withYear(LocalDate.now().getYear());
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDate.parse(normalized);
        } catch (DateTimeParseException ignored) {
        }
        throw new IllegalArgumentException("Unsupported semester start date: " + raw);
    }

    private LocalTime parseTime(String raw) {
        String normalized = raw == null ? "" : raw.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("时间不能为空");
        }
        try {
            return LocalTime.parse(normalized);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalTime.parse(normalized, new DateTimeFormatterBuilder()
                    .appendValue(ChronoField.HOUR_OF_DAY)
                    .appendLiteral(':')
                    .appendValue(ChronoField.MINUTE_OF_HOUR)
                    .toFormatter(Locale.ROOT));
        } catch (DateTimeParseException ignored) {
        }
        throw new IllegalArgumentException("Unsupported time: " + raw);
    }

    private LocalDateTime parseDateTime(String raw) {
        String normalized = raw == null ? "" : raw.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("截止时间不能为空");
        }

        try {
            return LocalDateTime.parse(normalized);
        } catch (DateTimeParseException ignored) {
        }

        try {
            return LocalDateTime.parse(normalized.replace('T', ' '), new DateTimeFormatterBuilder()
                    .appendValue(ChronoField.YEAR, 4)
                    .appendLiteral('-')
                    .appendValue(ChronoField.MONTH_OF_YEAR)
                    .appendLiteral('-')
                    .appendValue(ChronoField.DAY_OF_MONTH)
                    .appendLiteral(' ')
                    .appendValue(ChronoField.HOUR_OF_DAY)
                    .appendLiteral(':')
                    .appendValue(ChronoField.MINUTE_OF_HOUR)
                    .toFormatter(Locale.ROOT));
        } catch (DateTimeParseException ignored) {
        }

        try {
            return LocalDateTime.of(parseDate(normalized.substring(0, normalized.indexOf(' '))), parseTime(normalized.substring(normalized.indexOf(' ') + 1)));
        } catch (Exception ignored) {
        }

        throw new IllegalArgumentException("Unsupported due_at datetime: " + raw);
    }

    private DayOfWeek parseWeekday(String raw) {
        return switch (raw) {
            case "星期一" -> DayOfWeek.MONDAY;
            case "星期二" -> DayOfWeek.TUESDAY;
            case "星期三" -> DayOfWeek.WEDNESDAY;
            case "星期四" -> DayOfWeek.THURSDAY;
            case "星期五" -> DayOfWeek.FRIDAY;
            case "星期六" -> DayOfWeek.SATURDAY;
            case "星期日", "星期天" -> DayOfWeek.SUNDAY;
            default -> throw new IllegalArgumentException("Unsupported weekday: " + raw);
        };
    }

    private String buildSemesterName(LocalDate firstMonday) {
        return firstMonday.getYear() + " 学期";
    }

    private String normalizeColor(String colorHex, TaskType type) {
        return colorHex == null || colorHex.isBlank() ? defaultColor(type) : colorHex.trim();
    }

    private int toMinute(LocalTime time) {
        if (time == null) {
            throw new IllegalArgumentException("时间不能为空");
        }
        return time.getHour() * 60 + time.getMinute();
    }

    private String buildImportedCourseUuid(CourseTask course) {
        String seed = course.name()
                + "|" + course.weekday().getValue()
                + "|" + course.startSection()
                + "|" + course.endSection()
                + "|" + course.weekPattern()
                + "|" + course.location();
        return UUID.nameUUIDFromBytes(seed.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }

    private String buildImportedCustomUuid(TemplateCustomTask task) {
        String seed = "CUSTOM|"
                + task.title()
                + "|" + task.date()
                + "|" + task.startTime()
                + "|" + task.endTime()
                + "|" + task.location();
        return UUID.nameUUIDFromBytes(seed.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }

    private String buildImportedDeadlineUuid(TemplateDeadlineTask task) {
        String seed = "DDL|"
                + task.title()
                + "|" + task.dueAt()
                + "|" + task.location();
        return UUID.nameUUIDFromBytes(seed.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }

    private int normalizePriority(int priority) {
        if (priority < 1) {
            return 1;
        }
        return Math.min(priority, 5);
    }

    private String valueOrDefault(String raw, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return raw.trim();
    }

    private record ImportedSchedule(
            SemesterConfig config,
            List<CourseTask> courses,
            Map<Integer, TimeSection> sectionLookup,
            List<TemplateCustomTask> customTasks,
            List<TemplateDeadlineTask> deadlineTasks) {
    }

    private record TemplateCustomTask(
            String title,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            String location,
            String note,
            int priority,
            String colorHex) {
    }

    private record TemplateDeadlineTask(
            String title,
            LocalDateTime dueAt,
            String location,
            String note,
            int priority,
            String colorHex) {
    }
}
