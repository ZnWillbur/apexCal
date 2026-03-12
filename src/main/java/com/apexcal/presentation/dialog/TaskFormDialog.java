package com.apexcal.presentation.dialog;

import com.apexcal.application.service.ScheduleService;
import com.apexcal.domain.task.TaskDraft;
import com.apexcal.domain.task.TaskItem;
import com.apexcal.domain.task.TaskSource;
import com.apexcal.domain.task.TaskType;
import com.apexcal.domain.task.WeekPattern;
import java.net.URL;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.concurrent.atomic.AtomicReference;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;

public final class TaskFormDialog {
    private static final DateTimeFormatter FLEXIBLE_TIME = new DateTimeFormatterBuilder()
            .appendValue(ChronoField.HOUR_OF_DAY)
            .appendLiteral(':')
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
            .toFormatter();

    private final ScheduleService scheduleService;

    public TaskFormDialog(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    public TaskItem show(Window owner, TaskItem existing) {
        return show(owner, existing, TaskType.CUSTOM, LocalDate.now());
    }

    public TaskItem show(Window owner, TaskItem existing, TaskType preferredType, LocalDate preferredDate) {
        Dialog<TaskItem> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(existing == null ? "新建任务" : "编辑任务");
        dialog.setHeaderText(existing == null ? "创建新的课程 / 自建 / DDL 任务" : "修改任务内容");
        applyStyles(dialog);

        ButtonType saveButtonType = new ButtonType("保存", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        ComboBox<TaskType> typeBox = new ComboBox<>(FXCollections.observableArrayList(TaskType.values()));
        typeBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(TaskType value) {
                return value == null ? "" : value.displayName();
            }

            @Override
            public TaskType fromString(String string) {
                return null;
            }
        });

        TextField titleField = new TextField();
        TextField locationField = new TextField();
        Spinner<Integer> prioritySpinner = new Spinner<>(1, 5, existing == null ? 3 : existing.priority());
        TextArea noteArea = new TextArea();
        noteArea.setPrefRowCount(4);

        ComboBox<DayOfWeek> weekdayBox = new ComboBox<>(FXCollections.observableArrayList(DayOfWeek.values()));
        weekdayBox.setConverter(dayOfWeekConverter());
        TextField weekPatternField = new TextField();
        TextField recurringStartField = new TextField();
        TextField recurringEndField = new TextField();

        DatePicker datePicker = new DatePicker();
        TextField customStartField = new TextField();
        TextField customEndField = new TextField();

        DatePicker dueDatePicker = new DatePicker();
        TextField dueTimeField = new TextField();

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("form-error");
        errorLabel.setWrapText(true);

        VBox recurringBox = new VBox(10,
                labeled("星期", weekdayBox),
                labeled("周次", weekPatternField),
                labeled("开始时间", recurringStartField),
                labeled("结束时间", recurringEndField));
        VBox customBox = new VBox(10,
                labeled("日期", datePicker),
                labeled("开始时间", customStartField),
                labeled("结束时间", customEndField));
        VBox ddlBox = new VBox(10,
                labeled("截止日期", dueDatePicker),
                labeled("截止时间", dueTimeField));

        GridPane gridPane = new GridPane();
        gridPane.setHgap(18);
        gridPane.setVgap(14);
        gridPane.setPadding(new Insets(12, 0, 0, 0));
        gridPane.add(new Label("任务类型"), 0, 0);
        gridPane.add(typeBox, 1, 0);
        gridPane.add(new Label("标题"), 0, 1);
        gridPane.add(titleField, 1, 1);
        gridPane.add(new Label("地点"), 0, 2);
        gridPane.add(locationField, 1, 2);
        gridPane.add(new Label("优先级"), 0, 3);
        gridPane.add(prioritySpinner, 1, 3);
        gridPane.add(new Label("备注"), 0, 4);
        gridPane.add(noteArea, 1, 4);
        gridPane.add(recurringBox, 1, 5);
        gridPane.add(customBox, 1, 6);
        gridPane.add(ddlBox, 1, 7);
        gridPane.add(errorLabel, 1, 8);
        dialog.getDialogPane().setContent(gridPane);

        if (existing != null) {
            typeBox.setValue(existing.type());
            titleField.setText(existing.title());
            locationField.setText(existing.location());
            noteArea.setText(existing.note());
            if (existing.type() == TaskType.COURSE) {
                weekdayBox.setValue(existing.weekday());
                weekPatternField.setText(existing.weekPattern());
                recurringStartField.setText(existing.startTime().format(DateTimeFormatter.ofPattern("HH:mm")));
                recurringEndField.setText(existing.endTime().format(DateTimeFormatter.ofPattern("HH:mm")));
            } else if (existing.type() == TaskType.CUSTOM) {
                datePicker.setValue(existing.startDate());
                customStartField.setText(existing.startTime().format(DateTimeFormatter.ofPattern("HH:mm")));
                customEndField.setText(existing.endTime().format(DateTimeFormatter.ofPattern("HH:mm")));
            } else {
                dueDatePicker.setValue(existing.dueAt().toLocalDate());
                dueTimeField.setText(existing.dueAt().toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")));
            }
            if (existing.source() == TaskSource.IMPORTED_CLASS_JSON) {
                typeBox.setDisable(true);
            }
        } else {
            TaskType initialType = preferredType == null ? TaskType.CUSTOM : preferredType;
            LocalDate initialDate = preferredDate == null ? LocalDate.now() : preferredDate;
            typeBox.setValue(initialType);
            recurringStartField.setText("08:00");
            recurringEndField.setText("09:30");
            customStartField.setText("19:00");
            customEndField.setText("20:00");
            dueTimeField.setText("23:59");
            datePicker.setValue(initialDate);
            dueDatePicker.setValue(initialDate);
            weekdayBox.setValue(initialDate.getDayOfWeek());
        }

        Runnable refreshVisibility = () -> {
            TaskType type = typeBox.getValue();
            recurringBox.setManaged(type == TaskType.COURSE);
            recurringBox.setVisible(type == TaskType.COURSE);
            customBox.setManaged(type == TaskType.CUSTOM);
            customBox.setVisible(type == TaskType.CUSTOM);
            ddlBox.setManaged(type == TaskType.DDL);
            ddlBox.setVisible(type == TaskType.DDL);
            errorLabel.setText("");
        };
        typeBox.valueProperty().addListener((observable, oldValue, newValue) -> refreshVisibility.run());
        refreshVisibility.run();

        AtomicReference<TaskItem> savedTaskRef = new AtomicReference<>();
        Node saveButton = dialog.getDialogPane().lookupButton(saveButtonType);
        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                TaskDraft draft = switch (typeBox.getValue()) {
                    case COURSE -> new TaskDraft(
                            TaskType.COURSE,
                            requireText(titleField, "标题"),
                            noteArea.getText(),
                            locationField.getText(),
                            null,
                            prioritySpinner.getValue(),
                            requireValue(weekdayBox.getValue(), "星期"),
                            normalizeWeekPattern(weekPatternField.getText()),
                            null,
                            parseTime(recurringStartField.getText(), "开始时间"),
                            parseTime(recurringEndField.getText(), "结束时间"),
                            null,
                            null);
                    case CUSTOM -> new TaskDraft(
                            TaskType.CUSTOM,
                            requireText(titleField, "标题"),
                            noteArea.getText(),
                            locationField.getText(),
                            null,
                            prioritySpinner.getValue(),
                            null,
                            "",
                            requireValue(datePicker.getValue(), "日期"),
                            parseTime(customStartField.getText(), "开始时间"),
                            parseTime(customEndField.getText(), "结束时间"),
                            null,
                            null);
                    case DDL -> new TaskDraft(
                            TaskType.DDL,
                            requireText(titleField, "标题"),
                            noteArea.getText(),
                            locationField.getText(),
                            null,
                            prioritySpinner.getValue(),
                            null,
                            "",
                            null,
                            null,
                            null,
                            requireValue(dueDatePicker.getValue(), "截止日期"),
                            parseTime(dueTimeField.getText(), "截止时间"));
                };
                validateDraft(draft);
                savedTaskRef.set(existing == null ? scheduleService.createTask(draft) : scheduleService.updateTask(existing.uuid(), draft));
            } catch (Exception exception) {
                errorLabel.setText(exception.getMessage());
                event.consume();
            }
        });

        dialog.setResultConverter(buttonType -> buttonType == saveButtonType ? savedTaskRef.get() : null);
        return dialog.showAndWait().orElse(null);
    }

    private void validateDraft(TaskDraft draft) {
        if (draft.type() != TaskType.DDL) {
            if (!draft.endTime().isAfter(draft.startTime())) {
                throw new IllegalArgumentException("结束时间必须晚于开始时间");
            }
        }
    }

    private VBox labeled(String labelText, Node node) {
        VBox box = new VBox(6, new Label(labelText), node);
        return box;
    }

    private String normalizeWeekPattern(String raw) {
        String normalized = requireText(raw, "周次");
        WeekPattern.parse(normalized);
        return normalized;
    }

    private LocalTime parseTime(String raw, String fieldName) {
        try {
            return LocalTime.parse(requireText(raw, fieldName), FLEXIBLE_TIME);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(fieldName + "格式应为 HH:mm");
        }
    }

    private String requireText(TextField field, String fieldName) {
        return requireText(field.getText(), fieldName);
    }

    private String requireText(String text, String fieldName) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return text.trim();
    }

    private <T> T requireValue(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value;
    }

    private StringConverter<DayOfWeek> dayOfWeekConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(DayOfWeek value) {
                if (value == null) {
                    return "";
                }
                return switch (value) {
                    case MONDAY -> "星期一";
                    case TUESDAY -> "星期二";
                    case WEDNESDAY -> "星期三";
                    case THURSDAY -> "星期四";
                    case FRIDAY -> "星期五";
                    case SATURDAY -> "星期六";
                    case SUNDAY -> "星期日";
                };
            }

            @Override
            public DayOfWeek fromString(String string) {
                return null;
            }
        };
    }

    private void applyStyles(Dialog<?> dialog) {
        URL stylesheet = Thread.currentThread().getContextClassLoader().getResource("css/app.css");
        if (stylesheet != null) {
            dialog.getDialogPane().getStylesheets().add(stylesheet.toExternalForm());
        }
        dialog.getDialogPane().getStyleClass().add("app-dialog");
    }
}