package com.apexcal.presentation.dialog;

import com.apexcal.application.service.ScheduleService;
import com.apexcal.domain.task.TaskItem;
import com.apexcal.domain.task.TaskSource;
import com.apexcal.domain.task.TaskType;
import com.apexcal.presentation.window.WindowTheme;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;

public final class TaskOverviewDialog {
    private final ScheduleService scheduleService;
    private final Runnable onTasksChanged;

    public TaskOverviewDialog(ScheduleService scheduleService, Runnable onTasksChanged) {
        this.scheduleService = scheduleService;
        this.onTasksChanged = onTasksChanged;
    }

    public void show(Window owner) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("任务总览");
        dialog.setHeaderText("查看全部任务，并可直接筛选、编辑或删除");
        applyStyles(dialog);
        dialog.getDialogPane().getButtonTypes().add(new ButtonType("关闭", ButtonBar.ButtonData.CANCEL_CLOSE));

        ObservableList<TaskItem> rows = FXCollections.observableArrayList();
        TableView<TaskItem> tableView = new TableView<>(rows);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        TableColumn<TaskItem, String> titleColumn = new TableColumn<>("标题");
        titleColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().title()));

        TableColumn<TaskItem, String> typeColumn = new TableColumn<>("类型");
        typeColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().type().displayName()));

        TableColumn<TaskItem, String> timeColumn = new TableColumn<>("时间");
        timeColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(describeTime(cell.getValue())));

        TableColumn<TaskItem, String> locationColumn = new TableColumn<>("地点");
        locationColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().location()));

        TableColumn<TaskItem, String> sourceColumn = new TableColumn<>("来源");
        sourceColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().source() == TaskSource.USER ? "用户创建" : "课程导入"));

        tableView.getColumns().setAll(List.of(titleColumn, typeColumn, timeColumn, locationColumn, sourceColumn));

        ComboBox<TaskFilter> filterBox = new ComboBox<>(FXCollections.observableArrayList(TaskFilter.values()));
        filterBox.setValue(TaskFilter.ALL);
        filterBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(TaskFilter value) {
                return value == null ? "" : value.label;
            }

            @Override
            public TaskFilter fromString(String string) {
                return null;
            }
        });

        TaskFormDialog formDialog = new TaskFormDialog(scheduleService);
        Button newButton = new Button("新建任务");
        newButton.getStyleClass().add("primary-button");
        newButton.setOnAction(event -> {
            if (formDialog.show(owner, null) != null) {
                refreshRows(rows, filterBox.getValue());
                onTasksChanged.run();
            }
        });

        Button editButton = new Button("编辑");
        editButton.getStyleClass().add("secondary-button");
        editButton.setOnAction(event -> editSelected(tableView, owner, formDialog, rows, filterBox.getValue()));

        Button deleteButton = new Button("删除");
        deleteButton.getStyleClass().add("secondary-button");
        deleteButton.setOnAction(event -> {
            TaskItem selected = tableView.getSelectionModel().getSelectedItem();
            if (selected == null) {
                return;
            }
            scheduleService.deleteTask(selected.uuid());
            refreshRows(rows, filterBox.getValue());
            onTasksChanged.run();
        });

        tableView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                editSelected(tableView, owner, formDialog, rows, filterBox.getValue());
            }
        });

        filterBox.valueProperty().addListener((observable, oldValue, newValue) -> refreshRows(rows, newValue));
        refreshRows(rows, filterBox.getValue());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox toolbar = new HBox(10, new Label("筛选"), filterBox, spacer, newButton, editButton, deleteButton);
        VBox root = new VBox(16, toolbar, tableView);
        root.setPadding(new Insets(12, 0, 0, 0));
        VBox.setVgrow(tableView, Priority.ALWAYS);

        dialog.getDialogPane().setContent(root);
        dialog.showAndWait();
    }

    private void editSelected(TableView<TaskItem> tableView, Window owner, TaskFormDialog formDialog, ObservableList<TaskItem> rows, TaskFilter filter) {
        TaskItem selected = tableView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        if (formDialog.show(owner, selected) != null) {
            refreshRows(rows, filter);
            onTasksChanged.run();
        }
    }

    private void refreshRows(ObservableList<TaskItem> rows, TaskFilter filter) {
        rows.setAll(scheduleService.listAllTasks().stream().filter(filter::matches).toList());
    }

    private String describeTime(TaskItem task) {
        return switch (task.type()) {
            case COURSE -> toChineseDay(task.weekday()) + " " + task.formattedTimeRange() + " · 周次 " + task.weekPattern();
            case CUSTOM -> task.startDate() + " " + task.formattedTimeRange();
            case DDL -> task.dueAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        };
    }

    private String toChineseDay(java.time.DayOfWeek dayOfWeek) {
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

    private void applyStyles(Dialog<?> dialog) {
        WindowTheme.applyDialogTheme(dialog);
    }

    private enum TaskFilter {
        ALL("全部") {
            @Override
            boolean matches(TaskItem task) {
                return true;
            }
        },
        COURSE("课程") {
            @Override
            boolean matches(TaskItem task) {
                return task.type() == TaskType.COURSE;
            }
        },
        CUSTOM("自建") {
            @Override
            boolean matches(TaskItem task) {
                return task.type() == TaskType.CUSTOM;
            }
        },
        DDL("DDL") {
            @Override
            boolean matches(TaskItem task) {
                return task.type() == TaskType.DDL;
            }
        };

        private final String label;

        TaskFilter(String label) {
            this.label = label;
        }

        abstract boolean matches(TaskItem task);
    }
}