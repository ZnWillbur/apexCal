package com.apexcal.presentation.dialog;

import com.apexcal.application.service.AppConfigService;
import com.apexcal.application.service.ScheduleService;
import com.apexcal.domain.semester.SemesterConfig;
import com.apexcal.infrastructure.config.AppDirectories;
import java.net.URL;
import java.time.LocalDate;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
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
        TextArea infoArea = new TextArea();
        infoArea.setEditable(false);
        infoArea.setWrapText(true);
        infoArea.setPrefRowCount(5);
        infoArea.setText("数据目录：" + AppDirectories.dataDirectory().toAbsolutePath() + System.lineSeparator()
                + "日志目录：" + AppDirectories.logDirectory().toAbsolutePath() + System.lineSeparator()
                + "当前学期：" + config.semesterName() + System.lineSeparator()
                + "关闭主界面后程序会隐藏到托盘，并按需显示桌面小窗。");

        Label statusLabel = new Label();
        statusLabel.getStyleClass().add("form-error");

        Button reimportButton = new Button("重新导入 config/import/class.json / time.json");
        reimportButton.getStyleClass().add("secondary-button");
        reimportButton.setOnAction(event -> {
            try {
                scheduleService.reloadExternalCourseData();
                SemesterConfig refreshed = scheduleService.getSemesterConfig();
                firstMondayPicker.setValue(refreshed.firstMonday());
                statusLabel.setText("已重新导入课程与节次模板");
                onChanged.run();
            } catch (Exception exception) {
                statusLabel.setText(exception.getMessage());
            }
        });

        GridPane gridPane = new GridPane();
        gridPane.setHgap(18);
        gridPane.setVgap(14);
        gridPane.setPadding(new Insets(12, 0, 0, 0));
        gridPane.add(new Label("学期第一周周一"), 0, 0);
        gridPane.add(firstMondayPicker, 1, 0);
        gridPane.add(startupCheckBox, 1, 1);
        gridPane.add(new Label("运行信息"), 0, 2);
        gridPane.add(infoArea, 1, 2);
        gridPane.add(new HBox(reimportButton), 1, 3);
        gridPane.add(statusLabel, 1, 4);
        GridPane.setHgrow(infoArea, Priority.ALWAYS);
        dialog.getDialogPane().setContent(gridPane);

        Node saveButton = dialog.getDialogPane().lookupButton(saveButtonType);
        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                LocalDate firstMonday = firstMondayPicker.getValue();
                if (firstMonday == null) {
                    throw new IllegalArgumentException("学期起始日期不能为空");
                }
                scheduleService.updateFirstMonday(firstMonday);
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
        URL stylesheet = Thread.currentThread().getContextClassLoader().getResource("css/app.css");
        if (stylesheet != null) {
            dialog.getDialogPane().getStylesheets().add(stylesheet.toExternalForm());
        }
        dialog.getDialogPane().getStyleClass().add("app-dialog");
    }
}