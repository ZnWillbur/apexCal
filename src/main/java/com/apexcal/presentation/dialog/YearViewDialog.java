package com.apexcal.presentation.dialog;

import com.apexcal.application.service.ScheduleService;
import com.apexcal.domain.service.ColorGradientService;
import com.apexcal.presentation.window.WindowTheme;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

public final class YearViewDialog {
    private final ScheduleService scheduleService;
    private final Runnable onTasksChanged;
    private final ColorGradientService colorGradientService = new ColorGradientService();

    public YearViewDialog(ScheduleService scheduleService, Runnable onTasksChanged) {
        this.scheduleService = scheduleService;
        this.onTasksChanged = onTasksChanged;
    }

    public void show(Window owner) {
        show(owner, java.time.LocalDate.now().getYear());
    }

    public void show(Window owner, int initialYear) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("年视图");
        dialog.setHeaderText("按年查看任务分布，点击月份进入对应月视图");
        applyStyles(dialog);
        dialog.getDialogPane().getButtonTypes().add(new ButtonType("关闭", ButtonBar.ButtonData.CANCEL_CLOSE));

        AtomicInteger currentYear = new AtomicInteger(initialYear);
        MonthViewDialog monthViewDialog = new MonthViewDialog(scheduleService, onTasksChanged);

        Label yearLabel = new Label();
        yearLabel.getStyleClass().add("headline-small");
        Label summaryLabel = new Label();
        summaryLabel.getStyleClass().add("lead-small");

        Button previousButton = new Button("上一年");
        previousButton.getStyleClass().add("secondary-button");
        Button nextButton = new Button("下一年");
        nextButton.getStyleClass().add("secondary-button");

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(14);
        grid.getStyleClass().add("year-grid");

        Runnable render = () -> renderYear(dialog, grid, yearLabel, summaryLabel, currentYear.get(), monthViewDialog);
        previousButton.setOnAction(event -> {
            currentYear.decrementAndGet();
            render.run();
        });
        nextButton.setOnAction(event -> {
            currentYear.incrementAndGet();
            render.run();
        });

        Region spacer = new Region();
        javafx.scene.layout.HBox.setHgrow(spacer, Priority.ALWAYS);
        javafx.scene.layout.HBox toolbar = new javafx.scene.layout.HBox(10, previousButton, nextButton, spacer, yearLabel);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        Label hintLabel = new Label("色块越深代表整月越忙。点击月份卡片可进入月视图，继续查看每日任务。\n适合快速定位考试月、作业月和课程密集月。");
        hintLabel.getStyleClass().add("hint-text");
        hintLabel.setWrapText(true);

        VBox root = new VBox(16, toolbar, summaryLabel, grid, hintLabel);
        root.setPadding(new Insets(12, 0, 0, 0));
        dialog.getDialogPane().setContent(root);

        render.run();
        dialog.showAndWait();
    }

    private void renderYear(
            Dialog<?> dialog,
            GridPane grid,
            Label yearLabel,
            Label summaryLabel,
            int year,
            MonthViewDialog monthViewDialog) {
        grid.getChildren().clear();
        grid.getColumnConstraints().clear();
        grid.getRowConstraints().clear();

        for (int columnIndex = 0; columnIndex < 4; columnIndex++) {
            ColumnConstraints constraints = new ColumnConstraints(220);
            constraints.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(constraints);
        }
        for (int rowIndex = 0; rowIndex < 3; rowIndex++) {
            RowConstraints constraints = new RowConstraints(122);
            constraints.setVgrow(Priority.ALWAYS);
            grid.getRowConstraints().add(constraints);
        }

        Map<YearMonth, Integer> counts = scheduleService.buildYearTaskCounts(year);
        int maxCount = counts.values().stream().max(Comparator.naturalOrder()).orElse(0);
        int totalCount = counts.values().stream().mapToInt(Integer::intValue).sum();

        yearLabel.setText(year + " 年总览");
        summaryLabel.setText("全年累计 " + totalCount + " 项任务，最高月份 " + maxCount + " 项。点击月份卡片可继续下钻到月视图。");

        for (int monthValue = 1; monthValue <= 12; monthValue++) {
            YearMonth month = YearMonth.of(year, monthValue);
            int monthCount = counts.getOrDefault(month, 0);

            Label monthLabel = new Label(monthValue + " 月");
            monthLabel.getStyleClass().add("month-card-title");
            Label countLabel = new Label(monthCount + " 项任务");
            countLabel.getStyleClass().add("month-card-count");
            Label averageLabel = new Label("日均 " + (month.lengthOfMonth() == 0 ? 0 : monthCount / month.lengthOfMonth()) + " 项");
            averageLabel.getStyleClass().add("lead-small");

            VBox card = new VBox(10, monthLabel, countLabel, averageLabel);
            card.getStyleClass().add("month-card");
            if (year == java.time.LocalDate.now().getYear() && monthValue == java.time.LocalDate.now().getMonthValue()) {
                card.getStyleClass().add("month-card-current");
            }
            card.setPadding(new Insets(16));
            card.setAlignment(Pos.CENTER_LEFT);
            card.setStyle(colorGradientService.backgroundStyle(monthCount, maxCount)
                    + " -fx-background-radius: 22px;"
                    + " -fx-border-radius: 22px;");
            card.setOnMouseClicked(event -> {
                monthViewDialog.show(dialog.getDialogPane().getScene().getWindow(), month);
                onTasksChanged.run();
                renderYear(dialog, grid, yearLabel, summaryLabel, year, monthViewDialog);
            });

            int columnIndex = (monthValue - 1) % 4;
            int rowIndex = (monthValue - 1) / 4;
            grid.add(card, columnIndex, rowIndex);
        }
    }

    private void applyStyles(Dialog<?> dialog) {
        WindowTheme.applyDialogTheme(dialog);
    }
}