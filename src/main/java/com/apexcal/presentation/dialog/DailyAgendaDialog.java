package com.apexcal.presentation.dialog;

import com.apexcal.application.service.ScheduleService;
import com.apexcal.domain.task.TaskItem;
import com.apexcal.domain.task.TaskOccurrence;
import com.apexcal.domain.task.TaskType;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

public final class DailyAgendaDialog {
    private static final DateTimeFormatter DEADLINE_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final ScheduleService scheduleService;
    private final Runnable onTasksChanged;

    public DailyAgendaDialog(ScheduleService scheduleService, Runnable onTasksChanged) {
        this.scheduleService = scheduleService;
        this.onTasksChanged = onTasksChanged;
    }

    public void show(Window owner, LocalDate date) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("当日日程");
        dialog.setHeaderText(date + " 的课程、自建任务与 DDL");
        applyStyles(dialog);
        dialog.getDialogPane().getButtonTypes().add(new ButtonType("关闭", ButtonBar.ButtonData.CANCEL_CLOSE));

        ObservableList<AgendaEntry> rows = FXCollections.observableArrayList(buildRows(date));
        TableView<AgendaEntry> tableView = new TableView<>(rows);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        TableColumn<AgendaEntry, String> titleColumn = new TableColumn<>("标题");
        titleColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().task().title()));

        TableColumn<AgendaEntry, String> typeColumn = new TableColumn<>("类型");
        typeColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().typeLabel()));

        TableColumn<AgendaEntry, String> timeColumn = new TableColumn<>("时间");
        timeColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().timeLabel()));

        TableColumn<AgendaEntry, String> locationColumn = new TableColumn<>("地点");
        locationColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().task().location()));

        TableColumn<AgendaEntry, String> noteColumn = new TableColumn<>("备注");
        noteColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().task().note()));

        tableView.getColumns().setAll(List.of(titleColumn, typeColumn, timeColumn, locationColumn, noteColumn));

        TaskFormDialog formDialog = new TaskFormDialog(scheduleService);

        Button newTaskButton = new Button("新建任务");
        newTaskButton.getStyleClass().add("primary-button");
        newTaskButton.setOnAction(event -> {
            if (formDialog.show(owner, null, TaskType.CUSTOM, date) != null) {
                refreshRows(rows, date);
            }
        });

        Button newDeadlineButton = new Button("新建 DDL");
        newDeadlineButton.getStyleClass().add("secondary-button");
        newDeadlineButton.setOnAction(event -> {
            if (formDialog.show(owner, null, TaskType.DDL, date) != null) {
                refreshRows(rows, date);
            }
        });

        Button editButton = new Button("编辑");
        editButton.getStyleClass().add("secondary-button");
        editButton.setOnAction(event -> {
            AgendaEntry selected = tableView.getSelectionModel().getSelectedItem();
            if (selected == null) {
                return;
            }
            if (formDialog.show(owner, selected.task(), selected.task().type(), date) != null) {
                refreshRows(rows, date);
            }
        });

        Button deleteButton = new Button("删除");
        deleteButton.getStyleClass().add("secondary-button");
        deleteButton.setOnAction(event -> {
            AgendaEntry selected = tableView.getSelectionModel().getSelectedItem();
            if (selected == null) {
                return;
            }
            scheduleService.deleteTask(selected.task().uuid());
            refreshRows(rows, date);
        });

        tableView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                AgendaEntry selected = tableView.getSelectionModel().getSelectedItem();
                if (selected != null && formDialog.show(owner, selected.task(), selected.task().type(), date) != null) {
                    refreshRows(rows, date);
                }
            }
        });

        Label helperLabel = new Label("双击行可直接编辑。新建任务默认落在当前日期，也可在表单中切换类型。\n若删除导入课程，可在设置中重新导入 config/import/class.json / time.json 恢复。");
        helperLabel.getStyleClass().add("hint-text");
        helperLabel.setWrapText(true);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox toolbar = new HBox(10, newTaskButton, newDeadlineButton, spacer, editButton, deleteButton);

        VBox root = new VBox(16, toolbar, tableView, helperLabel);
        root.setPadding(new Insets(12, 0, 0, 0));
        VBox.setVgrow(tableView, Priority.ALWAYS);
        dialog.getDialogPane().setContent(root);
        dialog.showAndWait();
    }

    private List<AgendaEntry> buildRows(LocalDate date) {
        List<AgendaEntry> rows = new ArrayList<>();
        for (TaskOccurrence occurrence : scheduleService.listOccurrencesForDate(date)) {
            TaskItem task = occurrence.task();
            rows.add(new AgendaEntry(task, task.type().displayName(), occurrence.formattedTimeRange(), occurrence.startMinute()));
        }
        for (TaskItem deadline : scheduleService.listDeadlinesForDate(date)) {
            rows.add(new AgendaEntry(
                    deadline,
                    deadline.type().displayName(),
                    "截止 " + deadline.dueAt().toLocalTime().format(DEADLINE_FORMATTER),
                    1440 + deadline.dueAt().toLocalTime().getHour() * 60 + deadline.dueAt().toLocalTime().getMinute()));
        }
        rows.sort(Comparator.comparingInt(AgendaEntry::sortOrder).thenComparing(entry -> entry.task().title()));
        return rows;
    }

    private void refreshRows(ObservableList<AgendaEntry> rows, LocalDate date) {
        rows.setAll(buildRows(date));
        onTasksChanged.run();
    }

    private void applyStyles(Dialog<?> dialog) {
        URL stylesheet = Thread.currentThread().getContextClassLoader().getResource("css/app.css");
        if (stylesheet != null) {
            dialog.getDialogPane().getStylesheets().add(stylesheet.toExternalForm());
        }
        dialog.getDialogPane().getStyleClass().add("app-dialog");
    }

    private record AgendaEntry(TaskItem task, String typeLabel, String timeLabel, int sortOrder) {
    }
}