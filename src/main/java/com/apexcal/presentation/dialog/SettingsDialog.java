package com.apexcal.presentation.dialog;

import com.apexcal.application.service.AppConfigService;
import com.apexcal.application.service.ScheduleService;
import com.apexcal.domain.layout.TimeSection;
import com.apexcal.domain.semester.SemesterConfig;
import com.apexcal.infrastructure.config.AppDirectories;
import com.apexcal.presentation.window.WindowTheme;
import java.time.LocalTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

public final class SettingsDialog {
    private final ScheduleService scheduleService;
    private final AppConfigService appConfigService;
    private final Runnable onChanged;

    public SettingsDialog(ScheduleService scheduleService, AppConfigService appConfigService, Runnable onChanged) {
        this.scheduleService = scheduleService;
        this.appConfigService = appConfigService;
        this.onChanged = onChanged;
    }

    public void show(Window owner) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("设置");
        dialog.setHeaderText("学期设置、课程重导入与开机自启");
        applyStyles(dialog);

        ButtonType saveButtonType = new ButtonType("保存", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        SemesterConfig config = scheduleService.getSemesterConfig();
        DatePicker firstMondayPicker = new DatePicker(config.firstMonday());
        CheckBox startupCheckBox = new CheckBox("随系统开机启动 ApexCal");
        startupCheckBox.setSelected(appConfigService.isStartupEnabled());

        List<SectionInputRow> sectionRows = new ArrayList<>();
        VBox sectionRowsBox = new VBox(8);
        sectionRowsBox.getStyleClass().add("section-editor-card");

        Runnable refreshSectionIndices = () -> {
            for (int index = 0; index < sectionRows.size(); index++) {
                sectionRows.get(index).indexLabel().setText("第" + (index + 1) + "节");
            }
        };

        for (TimeSection section : config.sections()) {
            addSectionRow(sectionRows, sectionRowsBox, section.start().toString(), section.end().toString(), refreshSectionIndices);
        }

        Button addSectionButton = new Button("添加节次");
        addSectionButton.getStyleClass().add("secondary-button");
        addSectionButton.setOnAction(event -> addSectionRow(sectionRows, sectionRowsBox, "08:00", "08:40", refreshSectionIndices));

        ScrollPane sectionScrollPane = new ScrollPane(sectionRowsBox);
        sectionScrollPane.setFitToWidth(true);
        sectionScrollPane.setPannable(true);
        sectionScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sectionScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        sectionScrollPane.setPrefViewportHeight(210);
        sectionScrollPane.getStyleClass().add("widget-scroll");

        TextArea infoArea = new TextArea();
        infoArea.setEditable(false);
        infoArea.setWrapText(true);
        infoArea.setPrefRowCount(4);
        infoArea.setText("数据目录：" + AppDirectories.dataDirectory().toAbsolutePath() + System.lineSeparator()
                + "日志目录：" + AppDirectories.logDirectory().toAbsolutePath() + System.lineSeparator()
                + "当前学期：" + config.semesterName() + System.lineSeparator()
                + "关闭主界面后程序会隐藏到托盘，并按需显示桌面小窗。");

        Label statusLabel = new Label();
        statusLabel.getStyleClass().add("form-error");

        Button reimportButton = new Button("重新加载");
        reimportButton.getStyleClass().add("secondary-button");
        reimportButton.setOnAction(event -> {
            try {
                scheduleService.reloadExternalCourseData();
                SemesterConfig refreshed = scheduleService.getSemesterConfig();
                firstMondayPicker.setValue(refreshed.firstMonday());
                refillSectionRows(sectionRows, sectionRowsBox, refreshed.sections(), refreshSectionIndices);
                statusLabel.setText("已重新加载模板");
                onChanged.run();
            } catch (Exception exception) {
                statusLabel.setText(exception.getMessage());
            }
        });

        Button restoreDefaultsButton = new Button("恢复默认");
        restoreDefaultsButton.getStyleClass().add("secondary-button");
        restoreDefaultsButton.setOnAction(event -> {
            try {
                scheduleService.restoreBundledDefaults();
                SemesterConfig refreshed = scheduleService.getSemesterConfig();
                firstMondayPicker.setValue(refreshed.firstMonday());
                refillSectionRows(sectionRows, sectionRowsBox, refreshed.sections(), refreshSectionIndices);
                statusLabel.setText("已恢复系统默认模板");
                onChanged.run();
            } catch (Exception exception) {
                statusLabel.setText(exception.getMessage());
            }
        });

        GridPane gridPane = new GridPane();
        gridPane.setHgap(18);
        gridPane.setVgap(12);
        gridPane.setPadding(new Insets(12, 0, 0, 0));
        gridPane.add(new Label("学期第一周周一"), 0, 0);
        gridPane.add(firstMondayPicker, 1, 0);
        gridPane.add(startupCheckBox, 1, 1);

        Label sectionEditorLabel = new Label("节次编辑器");
        sectionEditorLabel.getStyleClass().add("card-title");
        HBox sectionActionRow = new HBox(10, sectionEditorLabel, new Region(), addSectionButton);
        sectionActionRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(sectionActionRow.getChildren().get(1), Priority.ALWAYS);

        gridPane.add(sectionActionRow, 1, 2);
        gridPane.add(sectionScrollPane, 1, 3);
        gridPane.add(new Label("运行信息"), 0, 4);
        gridPane.add(infoArea, 1, 4);
        HBox templateActions = new HBox(10, reimportButton, restoreDefaultsButton);
        templateActions.setAlignment(Pos.CENTER_LEFT);
        gridPane.add(templateActions, 1, 5);
        gridPane.add(statusLabel, 1, 6);
        GridPane.setHgrow(infoArea, Priority.ALWAYS);
        GridPane.setHgrow(sectionScrollPane, Priority.ALWAYS);
        dialog.getDialogPane().setContent(gridPane);

        Node saveButton = dialog.getDialogPane().lookupButton(saveButtonType);
        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                LocalDate firstMonday = firstMondayPicker.getValue();
                if (firstMonday == null) {
                    throw new IllegalArgumentException("学期起始日期不能为空");
                }

                List<TimeSection> sections = parseSections(sectionRows);

                scheduleService.updateSemesterConfig(firstMonday, sections);
                appConfigService.setStartupEnabled(startupCheckBox.isSelected());
                onChanged.run();
            } catch (Exception exception) {
                statusLabel.setText(exception.getMessage());
                event.consume();
            }
        });

        dialog.showAndWait();
    }

    private void applyStyles(Dialog<?> dialog) {
        WindowTheme.applyDialogTheme(dialog);
    }

    private void addSectionRow(
            List<SectionInputRow> sectionRows,
            VBox sectionRowsBox,
            String start,
            String end,
            Runnable refreshIndices) {
        Label indexLabel = new Label();
        indexLabel.getStyleClass().add("section-index-chip");

        TextField startField = new TextField(start);
        startField.setPromptText("08:00");
        startField.getStyleClass().add("section-time-input");

        TextField endField = new TextField(end);
        endField.setPromptText("08:40");
        endField.getStyleClass().add("section-time-input");

        Button removeButton = new Button("删除");
        removeButton.getStyleClass().add("secondary-button");

        Label arrowLabel = new Label("至");
        arrowLabel.getStyleClass().add("lead-small");

        HBox row = new HBox(8, indexLabel, startField, arrowLabel, endField, new Region(), removeButton);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("section-row");
        HBox.setHgrow(row.getChildren().get(4), Priority.ALWAYS);
        HBox.setHgrow(row.getChildren().get(5), Priority.NEVER);

        SectionInputRow inputRow = new SectionInputRow(indexLabel, startField, endField, row);
        removeButton.setOnAction(event -> {
            sectionRows.remove(inputRow);
            sectionRowsBox.getChildren().remove(row);
            refreshIndices.run();
        });

        sectionRows.add(inputRow);
        sectionRowsBox.getChildren().add(row);
        refreshIndices.run();
    }

    private void refillSectionRows(
            List<SectionInputRow> sectionRows,
            VBox sectionRowsBox,
            List<TimeSection> sections,
            Runnable refreshIndices) {
        sectionRows.clear();
        sectionRowsBox.getChildren().clear();
        for (TimeSection section : sections) {
            addSectionRow(sectionRows, sectionRowsBox, section.start().toString(), section.end().toString(), refreshIndices);
        }
    }

    private List<TimeSection> parseSections(List<SectionInputRow> sectionRows) {
        if (sectionRows.isEmpty()) {
            throw new IllegalArgumentException("至少保留一个节次");
        }

        List<TimeSection> sections = new ArrayList<>();
        LocalTime previousEnd = null;
        for (int index = 0; index < sectionRows.size(); index++) {
            SectionInputRow row = sectionRows.get(index);
            String startRaw = row.startField().getText() == null ? "" : row.startField().getText().trim();
            String endRaw = row.endField().getText() == null ? "" : row.endField().getText().trim();

            LocalTime start;
            LocalTime end;
            try {
                start = LocalTime.parse(startRaw);
                end = LocalTime.parse(endRaw);
            } catch (Exception exception) {
                throw new IllegalArgumentException("第" + (index + 1) + "节时间格式应为 HH:mm");
            }

            if (!end.isAfter(start)) {
                throw new IllegalArgumentException("第" + (index + 1) + "节结束时间必须晚于开始时间");
            }
            if (previousEnd != null && start.isBefore(previousEnd)) {
                throw new IllegalArgumentException("第" + (index + 1) + "节开始时间不能早于上一节结束时间");
            }

            sections.add(new TimeSection(index + 1, start, end));
            previousEnd = end;
        }

        return sections;
    }

    private record SectionInputRow(
            Label indexLabel,
            TextField startField,
            TextField endField,
            HBox container) {
    }
}