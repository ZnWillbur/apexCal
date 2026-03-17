package com.apexcal.presentation.controller;

import com.apexcal.application.service.AppConfigService;
import com.apexcal.application.service.ScheduleService;
import com.apexcal.domain.layout.TimeSection;
import com.apexcal.domain.semester.SemesterConfig;
import com.apexcal.domain.semester.WeekSchedule;
import com.apexcal.domain.service.ColorGradientService;
import com.apexcal.domain.task.CourseMetadata;
import com.apexcal.domain.task.TaskItem;
import com.apexcal.domain.task.TaskOccurrence;
import com.apexcal.domain.task.TaskSource;
import com.apexcal.domain.task.TaskType;
import com.apexcal.presentation.dialog.DailyAgendaDialog;
import com.apexcal.presentation.dialog.SettingsDialog;
import com.apexcal.presentation.dialog.TaskFormDialog;
import com.apexcal.presentation.dialog.TaskOverviewDialog;
import com.apexcal.presentation.viewmodel.WeekViewModel;
import com.apexcal.presentation.window.WindowTheme;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

public final class MainWindowController {
    private enum ViewMode {
        WEEK,
        MONTH,
        YEAR
    }

    private static final DateTimeFormatter DEADLINE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @FXML
    private Label viewTitleLabel;

    @FXML
    private Label periodLabel;

    @FXML
    private Label rangeLabel;

    @FXML
    private Label sourceStatusLabel;

    @FXML
    private StackPane viewContentHost;

    @FXML
    private GridPane scheduleGrid;

    @FXML
    private Button weekViewButton;

    @FXML
    private Button monthViewButton;

    @FXML
    private Button yearViewButton;

    @FXML
    private VBox detailContainer;

    @FXML
    private Label detailTitleLabel;

    @FXML
    private Label detailTypeLabel;

    @FXML
    private Label detailLocationLabel;

    @FXML
    private Label detailTimeLabel;

    @FXML
    private Label detailWeekLabel;

    @FXML
    private Label detailNoteLabel;

    @FXML
    private Label detailMetaLabel;

    private ScheduleService scheduleService;
    private AppConfigService appConfigService;
    private WeekViewModel viewModel;
    private Runnable onDataChanged = () -> {
    };

    private final ColorGradientService colorGradientService = new ColorGradientService();

    private ViewMode currentViewMode = ViewMode.WEEK;
    private YearMonth currentMonth = YearMonth.now();
    private int currentYear = LocalDate.now().getYear();

    private YearMonth cachedMonthKey;
    private VBox cachedMonthNode;
    private ScheduleService.MonthOverview cachedMonthOverview;

    private Integer cachedYearKey;
    private VBox cachedYearNode;
    private ScheduleService.YearOverview cachedYearOverview;

    private Region selectedBlock;
    private TaskItem selectedTask;

    public void init(ScheduleService scheduleService, AppConfigService appConfigService, Runnable onDataChanged) {
        this.scheduleService = scheduleService;
        this.appConfigService = appConfigService;
        this.onDataChanged = onDataChanged;
        this.viewModel = new WeekViewModel(scheduleService);

        sourceStatusLabel.textProperty().bind(viewModel.sourceLabelProperty());
        currentMonth = YearMonth.from(viewModel.currentWeekSchedule().visibleDates().getFirst());
        currentYear = currentMonth.getYear();

        viewContentHost.setOnMouseClicked(event -> {
            if (event.getTarget() == viewContentHost) {
                hideDetails();
            }
        });
        scheduleGrid.setOnMouseClicked(event -> {
            if (event.getTarget() == scheduleGrid) {
                hideDetails();
            }
        });

        hideDetails();
        switchViewMode(ViewMode.WEEK);
    }

    public void openNewTaskDialog(TaskType preferredType, LocalDate preferredDate) {
        openTaskDialog(null, preferredType, preferredDate);
    }

    public void openTaskOverviewDialog() {
        new TaskOverviewDialog(scheduleService, this::refreshAll).show(ownerWindow());
    }

    @FXML
    private void handlePreviousAction() {
        switch (currentViewMode) {
            case WEEK -> viewModel.previousWeek();
            case MONTH -> {
                currentMonth = currentMonth.minusMonths(1);
                currentYear = currentMonth.getYear();
            }
            case YEAR -> currentYear -= 1;
        }
        renderCurrentView();
    }

    @FXML
    private void handleNextAction() {
        switch (currentViewMode) {
            case WEEK -> viewModel.nextWeek();
            case MONTH -> {
                currentMonth = currentMonth.plusMonths(1);
                currentYear = currentMonth.getYear();
            }
            case YEAR -> currentYear += 1;
        }
        renderCurrentView();
    }

    @FXML
    private void handleWeekViewAction() {
        switchViewMode(ViewMode.WEEK);
    }

    @FXML
    private void handleMonthViewAction() {
        if (currentViewMode == ViewMode.WEEK) {
            currentMonth = YearMonth.from(viewModel.currentWeekSchedule().visibleDates().getFirst());
            currentYear = currentMonth.getYear();
        }
        switchViewMode(ViewMode.MONTH);
    }

    @FXML
    private void handleYearViewAction() {
        if (currentViewMode == ViewMode.WEEK) {
            currentYear = viewModel.currentWeekSchedule().visibleDates().getFirst().getYear();
            currentMonth = YearMonth.of(currentYear, 1);
        }
        switchViewMode(ViewMode.YEAR);
    }

    @FXML
    private void handleNewTaskAction() {
        openTaskDialog(null, TaskType.CUSTOM, LocalDate.now());
    }

    @FXML
    private void handleTaskOverviewAction() {
        openTaskOverviewDialog();
    }

    @FXML
    private void handleSettingsAction() {
        new SettingsDialog(scheduleService, appConfigService, this::refreshAll).show(ownerWindow());
    }

    @FXML
    private void handleCollapseDetailsAction() {
        hideDetails();
    }

    @FXML
    private void handleEditSelectedAction() {
        if (selectedTask == null) {
            return;
        }
        openTaskDialog(selectedTask, selectedTask.type(), preferredDateOf(selectedTask));
    }

    @FXML
    private void handleDeleteSelectedAction() {
        if (selectedTask == null || !confirmDelete(selectedTask)) {
            return;
        }
        scheduleService.deleteTask(selectedTask.uuid());
        hideDetails();
        refreshAll();
    }

    private void openTaskDialog(TaskItem existing, TaskType preferredType, LocalDate preferredDate) {
        TaskFormDialog dialog = new TaskFormDialog(scheduleService);
        TaskItem saved = dialog.show(ownerWindow(), existing, preferredType, preferredDate);
        if (saved != null) {
            selectedTask = saved;
            refreshAll();
        }
    }

    private void openDailyAgenda(LocalDate date) {
        new DailyAgendaDialog(scheduleService, this::refreshAll).show(ownerWindow(), date);
    }

    private LocalDate preferredDateOf(TaskItem task) {
        if (task.dueAt() != null) {
            return task.dueAt().toLocalDate();
        }
        if (task.startDate() != null) {
            return task.startDate();
        }
        return LocalDate.now();
    }

    private boolean confirmDelete(TaskItem task) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        WindowTheme.applyAlertTheme(alert);
        alert.initOwner(ownerWindow());
        alert.setTitle("删除任务");
        alert.setHeaderText("确认删除 “" + task.title() + "” 吗？");
        alert.setContentText(task.source() == TaskSource.IMPORTED_CLASS_JSON
                ? "这是从 config/import/class.json 导入的课程。删除后可在设置中重新导入恢复。"
                : "删除后无法撤销。");
        return alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    private void refreshAll() {
        viewModel.refresh();
        selectedTask = selectedTask == null ? null : scheduleService.findTask(selectedTask.uuid()).orElse(null);
        invalidateViewCaches();
        if (selectedTask == null) {
            hideDetails();
        }
        renderCurrentView();
        onDataChanged.run();
    }

    private void switchViewMode(ViewMode mode) {
        currentViewMode = mode;
        updateViewToggleState();
        if (mode != ViewMode.WEEK) {
            hideDetails();
        }
        renderCurrentView();
    }

    private void updateViewToggleState() {
        updateToggleButton(weekViewButton, currentViewMode == ViewMode.WEEK);
        updateToggleButton(monthViewButton, currentViewMode == ViewMode.MONTH);
        updateToggleButton(yearViewButton, currentViewMode == ViewMode.YEAR);
    }

    private void updateToggleButton(Button button, boolean active) {
        button.getStyleClass().remove("view-toggle-active");
        if (active) {
            button.getStyleClass().add("view-toggle-active");
        }
    }

    private void renderCurrentView() {
        switch (currentViewMode) {
            case WEEK -> renderWeekView();
            case MONTH -> renderMonthView();
            case YEAR -> renderYearView();
        }
    }

    private void renderWeekView() {
        viewTitleLabel.setText("周视图");

        WeekSchedule weekSchedule = viewModel.currentWeekSchedule();
        LocalDate start = weekSchedule.visibleDates().getFirst();
        LocalDate end = weekSchedule.visibleDates().getLast();

        periodLabel.setText("第" + weekSchedule.weekNumber() + "周");
        rangeLabel.setText(start + " 至 " + end + " · 点击任务可展开详情，点击空白可收起详情");

        viewContentHost.getChildren().setAll(scheduleGrid);
        renderWeek(weekSchedule);

        if (selectedTask != null) {
            showTaskDetails(selectedTask, null);
        }
    }

    private void renderMonthView() {
        viewTitleLabel.setText("月视图");
        periodLabel.setText(currentMonth.getYear() + "年 " + currentMonth.getMonthValue() + "月");

        if (cachedMonthOverview == null || !currentMonth.equals(cachedMonthKey)) {
            cachedMonthOverview = scheduleService.buildMonthOverview(currentMonth);
            cachedMonthNode = buildMonthViewNode(cachedMonthOverview.totalCounts(), cachedMonthOverview.deadlineCounts());
            cachedMonthKey = currentMonth;
        }

        rangeLabel.setText("本月累计 " + cachedMonthOverview.totalTasks() + " 项，峰值单日 "
                + cachedMonthOverview.maxDailyTasks() + " 项 · 点击日期可管理当天任务");

        viewContentHost.getChildren().setAll(cachedMonthNode);
    }

    private void renderYearView() {
        viewTitleLabel.setText("年视图");
        periodLabel.setText(currentYear + "年");

        if (cachedYearOverview == null || cachedYearKey == null || cachedYearKey != currentYear) {
            cachedYearOverview = scheduleService.buildYearOverview(currentYear);
            cachedYearNode = buildYearViewNode(cachedYearOverview.monthlyCounts(), cachedYearOverview.maxMonthlyTasks());
            cachedYearKey = currentYear;
        }

        rangeLabel.setText("全年累计 " + cachedYearOverview.totalTasks() + " 项，峰值月份 "
                + cachedYearOverview.maxMonthlyTasks() + " 项 · 点击月份可下钻月视图");

        viewContentHost.getChildren().setAll(cachedYearNode);
    }

    private void renderWeek(WeekSchedule weekSchedule) {
        SemesterConfig semesterConfig = viewModel.semesterConfig();
        List<TimeSection> sections = semesterConfig.sections();

        double rowMinHeight = 58;
        double headerMinHeight = 112;
        double expectedGridHeight = headerMinHeight + (sections.size() * rowMinHeight) + 2;
        scheduleGrid.setMinHeight(expectedGridHeight);
        scheduleGrid.setPrefHeight(expectedGridHeight);

        scheduleGrid.getChildren().clear();
        scheduleGrid.getColumnConstraints().clear();
        scheduleGrid.getRowConstraints().clear();

        ColumnConstraints timeColumn = new ColumnConstraints();
        timeColumn.setPercentWidth(12);
        scheduleGrid.getColumnConstraints().add(timeColumn);

        double dayPercent = weekSchedule.visibleDates().isEmpty() ? 88 : 88.0 / weekSchedule.visibleDates().size();
        for (int dayIndex = 0; dayIndex < weekSchedule.visibleDates().size(); dayIndex++) {
            ColumnConstraints dayColumn = new ColumnConstraints();
            dayColumn.setPercentWidth(dayPercent);
            dayColumn.setHgrow(Priority.ALWAYS);
            dayColumn.setFillWidth(true);
            scheduleGrid.getColumnConstraints().add(dayColumn);
        }

        RowConstraints headerRow = new RowConstraints();
        headerRow.setPercentHeight(9);
        headerRow.setMinHeight(headerMinHeight);
        headerRow.setPrefHeight(headerMinHeight);
        headerRow.setVgrow(Priority.ALWAYS);
        scheduleGrid.getRowConstraints().add(headerRow);

        scheduleGrid.add(createCornerCell(), 0, 0);
        List<LocalDate> visibleDates = weekSchedule.visibleDates();
        for (int dayIndex = 0; dayIndex < visibleDates.size(); dayIndex++) {
            LocalDate date = visibleDates.get(dayIndex);
            scheduleGrid.add(createDayHeader(date, weekSchedule.totalTaskCountFor(date), weekSchedule.ddlCountFor(date)), dayIndex + 1, 0);
        }

        double sectionPercent = sections.isEmpty() ? 0 : 91.0 / sections.size();
        for (int sectionIndex = 0; sectionIndex < sections.size(); sectionIndex++) {
            int rowIndex = sectionIndex + 1;
            TimeSection section = sections.get(sectionIndex);

            RowConstraints rowConstraints = new RowConstraints();
            rowConstraints.setPercentHeight(sectionPercent);
            rowConstraints.setVgrow(Priority.ALWAYS);
            rowConstraints.setMinHeight(rowMinHeight);
            scheduleGrid.getRowConstraints().add(rowConstraints);

            scheduleGrid.add(createTimeCell(section), 0, rowIndex);
            for (int dayIndex = 0; dayIndex < visibleDates.size(); dayIndex++) {
                scheduleGrid.add(createScheduleCell(), dayIndex + 1, rowIndex);
            }
        }

        for (int dayIndex = 0; dayIndex < visibleDates.size(); dayIndex++) {
            LocalDate date = visibleDates.get(dayIndex);
            for (TaskOccurrence occurrence : weekSchedule.occurrencesFor(date)) {
                Integer startRow = resolveStartRow(sections, occurrence);
                Integer endRow = resolveEndRow(sections, occurrence);
                if (startRow == null || endRow == null) {
                    continue;
                }
                StackPane block = createTaskBlock(occurrence);
                scheduleGrid.add(block, dayIndex + 1, startRow, 1, endRow - startRow + 1);
                GridPane.setFillHeight(block, true);
                GridPane.setFillWidth(block, true);
                GridPane.setVgrow(block, Priority.ALWAYS);
            }
        }
    }

    private VBox buildMonthViewNode(Map<LocalDate, Integer> counts, Map<LocalDate, Integer> ddlCounts) {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("calendar-grid");
        grid.setHgap(6);
        grid.setVgap(6);
        grid.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        for (int columnIndex = 0; columnIndex < 7; columnIndex++) {
            ColumnConstraints constraints = new ColumnConstraints();
            constraints.setPercentWidth(100.0 / 7);
            constraints.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(constraints);
        }

        RowConstraints weekdayRow = new RowConstraints();
        weekdayRow.setPercentHeight(12);
        weekdayRow.setVgrow(Priority.ALWAYS);
        weekdayRow.setMinHeight(38);
        grid.getRowConstraints().add(weekdayRow);
        for (int rowIndex = 0; rowIndex < 6; rowIndex++) {
            RowConstraints weekRow = new RowConstraints();
            weekRow.setPercentHeight(88.0 / 6);
            weekRow.setVgrow(Priority.ALWAYS);
            weekRow.setMinHeight(84);
            grid.getRowConstraints().add(weekRow);
        }

        List<String> dayNames = List.of("一", "二", "三", "四", "五", "六", "日");
        for (int columnIndex = 0; columnIndex < dayNames.size(); columnIndex++) {
            Label headerLabel = new Label("周" + dayNames.get(columnIndex));
            headerLabel.getStyleClass().add("calendar-weekday");
            VBox header = new VBox(headerLabel);
            header.getStyleClass().add("calendar-weekday-card");
            header.setAlignment(Pos.CENTER);
            header.setPadding(new Insets(10));
            grid.add(header, columnIndex, 0);
        }

        int maxCount = counts.values().stream().max(Comparator.naturalOrder()).orElse(0);
        int firstDayColumn = currentMonth.atDay(1).getDayOfWeek().getValue() - 1;
        int dayOfMonth = 1;
        LocalDate today = LocalDate.now();

        for (int weekIndex = 0; weekIndex < 6; weekIndex++) {
            for (int dayIndex = 0; dayIndex < 7; dayIndex++) {
                int cellIndex = weekIndex * 7 + dayIndex;
                if (cellIndex < firstDayColumn || dayOfMonth > currentMonth.lengthOfMonth()) {
                    Region placeholder = new Region();
                    placeholder.getStyleClass().add("calendar-cell-blank");
                    grid.add(placeholder, dayIndex, weekIndex + 1);
                    continue;
                }

                LocalDate date = currentMonth.atDay(dayOfMonth++);
                int taskCount = counts.getOrDefault(date, 0);
                int ddlCount = ddlCounts.getOrDefault(date, 0);

                Label dayLabel = new Label(Integer.toString(date.getDayOfMonth()));
                dayLabel.getStyleClass().add("calendar-day-number");

                Label countLabel = new Label(taskCount == 0 ? "空白日" : taskCount + " 项安排");
                countLabel.getStyleClass().add("calendar-day-summary");
                countLabel.setWrapText(true);

                Label ddlLabel = new Label(ddlCount == 0 ? "无 DDL" : "DDL " + ddlCount);
                ddlLabel.getStyleClass().add("calendar-day-deadline");
                ddlLabel.setWrapText(true);

                VBox cell = new VBox(4, dayLabel, countLabel, ddlLabel);
                cell.getStyleClass().add("calendar-cell");
                if (date.equals(today)) {
                    cell.getStyleClass().add("calendar-cell-today");
                }
                cell.setPadding(new Insets(8));
                cell.setMinHeight(84);
                cell.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
                cell.setStyle(colorGradientService.backgroundStyle(taskCount, maxCount)
                        + " -fx-background-radius: 18px;"
                        + " -fx-border-radius: 18px;");
                cell.setOnMouseClicked(event -> {
                    openDailyAgenda(date);
                    refreshAll();
                });
                grid.add(cell, dayIndex, weekIndex + 1);
            }
        }

        VBox root = new VBox(grid);
        root.getStyleClass().add("inline-view-card");
        root.setFillWidth(true);
        root.setMinHeight(640);
        root.setPrefHeight(640);
        root.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        VBox.setVgrow(grid, Priority.ALWAYS);
        return root;
    }

    private VBox buildYearViewNode(Map<YearMonth, Integer> counts, int maxCount) {
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.getStyleClass().add("year-grid");
        grid.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        for (int columnIndex = 0; columnIndex < 4; columnIndex++) {
            ColumnConstraints constraints = new ColumnConstraints();
            constraints.setPercentWidth(25);
            constraints.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(constraints);
        }
        for (int rowIndex = 0; rowIndex < 3; rowIndex++) {
            RowConstraints constraints = new RowConstraints();
            constraints.setPercentHeight(100.0 / 3);
            constraints.setVgrow(Priority.ALWAYS);
            constraints.setMinHeight(146);
            grid.getRowConstraints().add(constraints);
        }

        int currentMonthValue = LocalDate.now().getMonthValue();
        int currentSystemYear = LocalDate.now().getYear();
        for (int monthValue = 1; monthValue <= 12; monthValue++) {
            YearMonth month = YearMonth.of(currentYear, monthValue);
            int monthCount = counts.getOrDefault(month, 0);

            Label monthLabel = new Label(monthValue + " 月");
            monthLabel.getStyleClass().add("month-card-title");
            Label countLabel = new Label(monthCount + " 项任务");
            countLabel.getStyleClass().add("month-card-count");
            Label averageLabel = new Label("日均 " + (month.lengthOfMonth() == 0 ? 0 : monthCount / month.lengthOfMonth()) + " 项");
            averageLabel.getStyleClass().add("lead-small");

            VBox card = new VBox(6, monthLabel, countLabel, averageLabel);
            card.getStyleClass().add("month-card");
            if (currentYear == currentSystemYear && monthValue == currentMonthValue) {
                card.getStyleClass().add("month-card-current");
            }
            card.setPadding(new Insets(10));
            card.setAlignment(Pos.CENTER_LEFT);
            card.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            card.setStyle(colorGradientService.backgroundStyle(monthCount, maxCount)
                    + " -fx-background-radius: 22px;"
                    + " -fx-border-radius: 22px;");
            card.setOnMouseClicked(event -> {
                currentMonth = month;
                switchViewMode(ViewMode.MONTH);
            });

            int columnIndex = (monthValue - 1) % 4;
            int rowIndex = (monthValue - 1) / 4;
            grid.add(card, columnIndex, rowIndex);
        }

        VBox root = new VBox(grid);
        root.getStyleClass().add("inline-view-card");
        root.setFillWidth(true);
        root.setMinHeight(520);
        root.setPrefHeight(520);
        root.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        VBox.setVgrow(grid, Priority.ALWAYS);
        return root;
    }

    private StackPane createCornerCell() {
        Label label = new Label("时间 / 日期");
        label.getStyleClass().add("corner-title");
        StackPane pane = new StackPane(label);
        pane.getStyleClass().add("schedule-corner");
        return pane;
    }

    private VBox createDayHeader(LocalDate date, int totalCount, int ddlCount) {
        Label dayName = new Label(toChineseDayName(date.getDayOfWeek()));
        dayName.getStyleClass().add("day-name");

        Label dateLabel = new Label(date.getMonthValue() + "." + date.getDayOfMonth());
        dateLabel.getStyleClass().add("day-date");

        Label totalLabel = new Label(totalCount == 0 ? "空白日" : totalCount + " 项");
        totalLabel.getStyleClass().add("summary-chip");

        Label ddlLabel = new Label(ddlCount == 0 ? "无 DDL" : "DDL " + ddlCount);
        ddlLabel.getStyleClass().add("ddl-chip");

        VBox header = new VBox(4, dayName, dateLabel, totalLabel, ddlLabel);
        header.setPadding(new Insets(6, 6, 6, 6));
        header.setMinHeight(104);
        header.setAlignment(Pos.CENTER);
        header.getStyleClass().addAll("day-header", "day-header-clickable");
        header.setOnMouseClicked(event -> openDailyAgenda(date));
        return header;
    }

    private VBox createTimeCell(TimeSection section) {
        Label sectionLabel = new Label("第" + section.section() + "节");
        sectionLabel.getStyleClass().add("time-section-label");

        Label timeRangeLabel = new Label(section.formattedRange());
        timeRangeLabel.getStyleClass().add("time-range-label");

        VBox cell = new VBox(4, sectionLabel, timeRangeLabel);
        cell.setPadding(new Insets(6));
        cell.setAlignment(Pos.CENTER_LEFT);
        cell.getStyleClass().add("time-cell");
        return cell;
    }

    private StackPane createScheduleCell() {
        StackPane cell = new StackPane();
        cell.getStyleClass().add("schedule-cell");
        cell.setMinHeight(56);
        cell.setOnMouseClicked(event -> hideDetails());
        return cell;
    }

    private StackPane createTaskBlock(TaskOccurrence occurrence) {
        TaskItem task = occurrence.task();

        Label kindLabel = new Label(task.type().displayName());
        kindLabel.getStyleClass().add("task-kind-chip");

        Label titleLabel = new Label(task.title());
        titleLabel.getStyleClass().add("course-title");
        titleLabel.setWrapText(true);

        String secondaryText = task.location().isBlank() ? occurrence.formattedTimeRange() : task.location();
        Label subtitleLabel = new Label(secondaryText);
        subtitleLabel.getStyleClass().add("course-location");
        subtitleLabel.setWrapText(true);

        VBox content = new VBox(6, kindLabel, titleLabel, subtitleLabel);
        content.setPadding(new Insets(10));
        StackPane block = new StackPane(content);
        block.getStyleClass().add("schedule-block");
        block.setStyle(buildBlockStyle(task.colorHex()));
        block.setMinHeight(56);
        block.setMaxWidth(Double.MAX_VALUE);
        block.setOnMouseClicked(event -> showTaskDetails(task, block));
        return block;
    }

    private void showTaskDetails(TaskItem task, Region clickedBlock) {
        if (selectedBlock != null) {
            selectedBlock.getStyleClass().remove("schedule-block-selected");
        }
        selectedBlock = clickedBlock;
        if (selectedBlock != null) {
            selectedBlock.getStyleClass().add("schedule-block-selected");
        }

        selectedTask = task;
        detailContainer.setManaged(true);
        detailContainer.setVisible(true);

        detailTitleLabel.setText(task.title());
        detailTypeLabel.setText("类型：" + task.type().displayName() + " · 来源：" + sourceLabel(task.source()));
        detailLocationLabel.setText("地点：" + fillEmpty(task.location(), "未设置"));

        switch (task.type()) {
            case COURSE -> showCourseDetails(task);
            case CUSTOM -> showCustomDetails(task);
            case DDL -> showDeadlineDetails(task);
        }
    }

    private void showCourseDetails(TaskItem task) {
        CourseMetadata metadata = scheduleService.readCourseMetadata(task);
        detailTimeLabel.setText("时间：" + toChineseDayName(task.weekday()) + " " + task.formattedTimeRange());
        detailWeekLabel.setText("周次：" + fillEmpty(task.weekPattern(), "未设置"));
        detailNoteLabel.setText("备注：" + fillEmpty(task.note(), "无"));

        StringBuilder metaBuilder = new StringBuilder();
        if (!metadata.teacher().isBlank()) {
            metaBuilder.append("教师：").append(metadata.teacher());
        }
        if (!metadata.classesInfo().isBlank()) {
            if (metaBuilder.length() > 0) {
                metaBuilder.append("   ");
            }
            metaBuilder.append("班级：").append(metadata.classesInfo());
        }
        if (metadata.enrollment() > 0) {
            if (metaBuilder.length() > 0) {
                metaBuilder.append("   ");
            }
            metaBuilder.append("人数：").append(metadata.enrollment());
        }
        if (metaBuilder.length() > 0) {
            metaBuilder.append("   ");
        }
        metaBuilder.append("优先级：").append(task.priority());
        detailMetaLabel.setText(metaBuilder.toString());
    }

    private void showCustomDetails(TaskItem task) {
        detailTimeLabel.setText("时间：" + task.startDate() + " " + task.formattedTimeRange());
        detailWeekLabel.setText("星期：" + toChineseDayName(task.weekday()));
        detailNoteLabel.setText("备注：" + fillEmpty(task.note(), "无"));
        detailMetaLabel.setText("优先级：" + task.priority());
    }

    private void showDeadlineDetails(TaskItem task) {
        detailTimeLabel.setText("截止：" + task.dueAt().format(DEADLINE_FORMATTER));
        detailWeekLabel.setText("日期：" + task.dueAt().toLocalDate());
        detailNoteLabel.setText("备注：" + fillEmpty(task.note(), "无"));
        detailMetaLabel.setText("优先级：" + task.priority());
    }

    private void hideDetails() {
        if (selectedBlock != null) {
            selectedBlock.getStyleClass().remove("schedule-block-selected");
            selectedBlock = null;
        }
        selectedTask = null;

        detailContainer.setManaged(false);
        detailContainer.setVisible(false);

        detailTitleLabel.setText("点击任务查看详情");
        detailTypeLabel.setText("");
        detailLocationLabel.setText("");
        detailTimeLabel.setText("");
        detailWeekLabel.setText("");
        detailNoteLabel.setText("");
        detailMetaLabel.setText("");
    }

    private Integer resolveStartRow(List<TimeSection> sections, TaskOccurrence occurrence) {
        for (int index = 0; index < sections.size(); index++) {
            if (overlaps(occurrence, sections.get(index))) {
                return index + 1;
            }
        }
        return null;
    }

    private Integer resolveEndRow(List<TimeSection> sections, TaskOccurrence occurrence) {
        for (int index = sections.size() - 1; index >= 0; index--) {
            if (overlaps(occurrence, sections.get(index))) {
                return index + 1;
            }
        }
        return null;
    }

    private boolean overlaps(TaskOccurrence occurrence, TimeSection section) {
        return occurrence.startMinute() < section.endMinute() && occurrence.endMinute() > section.startMinute();
    }

    private String buildBlockStyle(String colorHex) {
        return "-fx-background-color: " + colorHex + ";"
                + " -fx-background-radius: 18px;"
                + " -fx-border-radius: 18px;"
                + " -fx-border-color: rgba(255, 255, 255, 0.18);";
    }

    private String sourceLabel(TaskSource source) {
        return source == TaskSource.USER ? "用户创建" : "课程导入";
    }

    private String fillEmpty(String raw, String fallback) {
        return raw == null || raw.isBlank() ? fallback : raw;
    }

    private void invalidateViewCaches() {
        cachedMonthKey = null;
        cachedMonthNode = null;
        cachedMonthOverview = null;

        cachedYearKey = null;
        cachedYearNode = null;
        cachedYearOverview = null;
    }

    private Window ownerWindow() {
        return viewContentHost.getScene() == null ? null : viewContentHost.getScene().getWindow();
    }

    private String toChineseDayName(DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case MONDAY -> "星期一";
            case TUESDAY -> "星期二";
            case WEDNESDAY -> "星期三";
            case THURSDAY -> "星期四";
            case FRIDAY -> "星期五";
            case SATURDAY -> "星期六";
            case SUNDAY -> "星期日";
        };
    }
}
