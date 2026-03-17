package com.apexcal.presentation.dialog;

import com.apexcal.application.service.ScheduleService;
import com.apexcal.domain.task.TaskItem;
import com.apexcal.presentation.window.WindowTheme;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

public final class DeadlineListDialog {
    private final ScheduleService scheduleService;
    private final Runnable onTasksChanged;

    public DeadlineListDialog(ScheduleService scheduleService, Runnable onTasksChanged) {
        this.scheduleService = scheduleService;
        this.onTasksChanged = onTasksChanged;
    }

    public void show(Window owner, LocalDate date) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("当日 DDL");
        dialog.setHeaderText(date + " 的 DDL 列表");
        applyStyles(dialog);
        dialog.getDialogPane().getButtonTypes().add(new ButtonType("关闭", ButtonBar.ButtonData.CANCEL_CLOSE));

        ObservableList<TaskItem> rows = FXCollections.observableArrayList(scheduleService.listDeadlinesForDate(date));
        TableView<TaskItem> tableView = new TableView<>(rows);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        TableColumn<TaskItem, String> titleColumn = new TableColumn<>("标题");
        titleColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().title()));
        TableColumn<TaskItem, String> dueColumn = new TableColumn<>("截止时间");
        dueColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().dueAt().format(DateTimeFormatter.ofPattern("HH:mm"))));
        TableColumn<TaskItem, String> locationColumn = new TableColumn<>("地点");
        locationColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().location()));
        TableColumn<TaskItem, String> noteColumn = new TableColumn<>("备注");
        noteColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().note()));

        tableView.getColumns().setAll(List.of(titleColumn, dueColumn, locationColumn, noteColumn));

        TaskFormDialog formDialog = new TaskFormDialog(scheduleService);

        Button newButton = new Button("新建 DDL");
        newButton.getStyleClass().add("primary-button");
        newButton.setOnAction(event -> {
            if (formDialog.show(owner, null) != null) {
                rows.setAll(scheduleService.listDeadlinesForDate(date));
                onTasksChanged.run();
            }
        });

        Button editButton = new Button("编辑");
        editButton.getStyleClass().add("secondary-button");
        editButton.setOnAction(event -> {
            TaskItem selected = tableView.getSelectionModel().getSelectedItem();
            if (selected == null) {
                return;
            }
            if (formDialog.show(owner, selected) != null) {
                rows.setAll(scheduleService.listDeadlinesForDate(date));
                onTasksChanged.run();
            }
        });

        Button deleteButton = new Button("删除");
        deleteButton.getStyleClass().add("secondary-button");
        deleteButton.setOnAction(event -> {
            TaskItem selected = tableView.getSelectionModel().getSelectedItem();
            if (selected == null) {
                return;
            }
            scheduleService.deleteTask(selected.uuid());
            rows.setAll(scheduleService.listDeadlinesForDate(date));
            onTasksChanged.run();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox toolbar = new HBox(10, newButton, spacer, editButton, deleteButton);
        VBox root = new VBox(16, toolbar, tableView);
        root.setPadding(new Insets(12, 0, 0, 0));
        VBox.setVgrow(tableView, Priority.ALWAYS);
        dialog.getDialogPane().setContent(root);
        dialog.showAndWait();
    }

    private void applyStyles(Dialog<?> dialog) {
        WindowTheme.applyDialogTheme(dialog);
    }
}