package com.apexcal.presentation.controller;

import com.apexcal.application.service.AppConfigService;
import com.apexcal.application.service.ScheduleService;
import com.apexcal.domain.layout.TimeSection;
import com.apexcal.domain.semester.SemesterConfig;
import com.apexcal.domain.semester.WeekSchedule;
import com.apexcal.domain.task.CourseMetadata;
import com.apexcal.domain.task.TaskItem;
import com.apexcal.domain.task.TaskOccurrence;
import com.apexcal.domain.task.TaskSource;
import com.apexcal.domain.task.TaskType;
import com.apexcal.presentation.dialog.DailyAgendaDialog;
import com.apexcal.presentation.dialog.MonthViewDialog;
import com.apexcal.presentation.dialog.SettingsDialog;
import com.apexcal.presentation.dialog.TaskFormDialog;
import com.apexcal.presentation.dialog.TaskOverviewDialog;
import com.apexcal.presentation.dialog.YearViewDialog;
import com.apexcal.presentation.viewmodel.WeekViewModel;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
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
    private static final DateTimeFormatter DEADLINE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @FXML
    private Label weekLabel;

    @FXML
    private Label rangeLabel;

    @FXML
    private Label sourceStatusLabel;

    @FXML
    private GridPane scheduleGrid;

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
    private Runnable onBack = () -> {
    };
    private Runnable onDataChanged = () -> {
    };
    private Region selectedBlock;
    private TaskItem selectedTask;

    public void init(ScheduleService scheduleService, AppConfigService appConfigService, Runnable onBack, Runnable onDataChanged) {
        this.scheduleService = scheduleService;
        this.appConfigService = appConfigService;
        this.onBack = onBack;
        this.onDataChanged = onDataChanged;
        this.viewModel = new WeekViewModel(scheduleService);
        weekLabel.textProperty().bind(viewModel.weekLabelProperty());
        rangeLabel.textProperty().bind(viewModel.rangeLabelProperty());
        sourceStatusLabel.textProperty().bind(viewModel.sourceLabelProperty());
        renderWeek();
        showEmptyDetails();
    }

    public void openNewTaskDialog(TaskType preferredType, LocalDate preferredDate) {
        openTaskDialog(null, preferredType, preferredDate);
    }

    public void openTaskOverviewDialog() {
        new TaskOverviewDialog(scheduleService, this::refreshAll).show(ownerWindow());
    }

    @FXML
    private void handlePreviousWeek() {
        viewModel.previousWeek();
        renderWeek();
        showEmptyDetails();
    }

    @FXML
    private void handleNextWeek() {
        viewModel.nextWeek();
        renderWeek();
        showEmptyDetails();
    }

    @FXML
    private void handleBackAction() {
        onBack.run();
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
    private void handleMonthViewAction() {
        new MonthViewDialog(scheduleService, this::refreshAll)
                .show(ownerWindow(), YearMonth.from(viewModel.currentWeekSchedule().visibleDates().getFirst()));
    }

    @FXML
    private void handleYearViewAction() {
        new YearViewDialog(scheduleService, this::refreshAll)
                .show(ownerWindow(), viewModel.currentWeekSchedule().visibleDates().getFirst().getYear());
    }

    @FXML
    private void handleSettingsAction() {
        new SettingsDialog(scheduleService, appConfigService, this::refreshAll).show(ownerWindow());
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
        selectedTask = null;
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
        renderWeek();
        if (selectedTask != null) {
            selectedTask = scheduleService.findTask(selectedTask.uuid()).orElse(null);
            if (selectedTask != null) {
                showTaskDetails(selectedTask, null);
            } else {
                showEmptyDetails();
            }
        } else {
            showEmptyDetails();
        }
        onDataChanged.run();
    }

    private void renderWeek() {
        SemesterConfig semesterConfig = viewModel.semesterConfig();
        WeekSchedule weekSchedule = viewModel.currentWeekSchedule();
        List<TimeSection> sections = semesterConfig.sections();

        scheduleGrid.getChildren().clear();
        scheduleGrid.getColumnConstraints().clear();
        scheduleGrid.getRowConstraints().clear();

        ColumnConstraints timeColumn = new ColumnConstraints(128);
        timeColumn.setMinWidth(128);
        scheduleGrid.getColumnConstraints().add(timeColumn);
        for (int dayIndex = 0; dayIndex < weekSchedule.visibleDates().size(); dayIndex++) {
            ColumnConstraints dayColumn = new ColumnConstraints(160);
            dayColumn.setHgrow(Priority.ALWAYS);
            dayColumn.setFillWidth(true);
            scheduleGrid.getColumnConstraints().add(dayColumn);
        }

        RowConstraints headerRow = new RowConstraints(104);
        scheduleGrid.getRowConstraints().add(headerRow);

        scheduleGrid.add(createCornerCell(), 0, 0);
        List<LocalDate> visibleDates = weekSchedule.visibleDates();
        for (int dayIndex = 0; dayIndex < visibleDates.size(); dayIndex++) {
            LocalDate date = visibleDates.get(dayIndex);
            scheduleGrid.add(createDayHeader(date, weekSchedule.totalTaskCountFor(date), weekSchedule.ddlCountFor(date)), dayIndex + 1, 0);
        }

        for (int sectionIndex = 0; sectionIndex < sections.size(); sectionIndex++) {
            int rowIndex = sectionIndex + 1;
            TimeSection section = sections.get(sectionIndex);

            RowConstraints rowConstraints = new RowConstraints(78);
            rowConstraints.setVgrow(Priority.NEVER);
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
            }
        }
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
        header.setPadding(new Insets(10, 8, 10, 8));
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
        cell.setPadding(new Insets(8));
        cell.setAlignment(Pos.CENTER_LEFT);
        cell.getStyleClass().add("time-cell");
        return cell;
    }

    private StackPane createScheduleCell() {
        StackPane cell = new StackPane();
        cell.setMinHeight(78);
        cell.getStyleClass().add("schedule-cell");
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
        block.setMinHeight(78);
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

    private void showEmptyDetails() {
        if (selectedBlock != null) {
            selectedBlock.getStyleClass().remove("schedule-block-selected");
            selectedBlock = null;
        }
        selectedTask = null;
        detailTitleLabel.setText("点击课表块或日期查看详情");
        detailTypeLabel.setText("类型：未选择任务");
        detailLocationLabel.setText("地点：未选择任务");
        detailTimeLabel.setText("时间：未选择任务");
        detailWeekLabel.setText("说明：点击日期可进入当日日程，点击课表块可直接查看和编辑详情。");
        detailNoteLabel.setText("备注：支持课程、自建任务和 DDL 统一管理。");
        detailMetaLabel.setText("提示：月视图和年视图已可用，托盘和桌面小窗会与这里共享同一份数据。");
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

    private Window ownerWindow() {
        return scheduleGrid.getScene() == null ? null : scheduleGrid.getScene().getWindow();
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
