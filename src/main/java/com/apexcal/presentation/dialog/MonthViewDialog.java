package com.apexcal.presentation.dialog;

import com.apexcal.application.service.ScheduleService;
import com.apexcal.domain.service.ColorGradientService;
import java.net.URL;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
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
import javafx.scene.layout.VBox;
import javafx.stage.Window;

public final class MonthViewDialog {
    private final ScheduleService scheduleService;
    private final Runnable onTasksChanged;
    private final ColorGradientService colorGradientService = new ColorGradientService();

    public MonthViewDialog(ScheduleService scheduleService, Runnable onTasksChanged) {
        this.scheduleService = scheduleService;
        this.onTasksChanged = onTasksChanged;
    }

    public void show(Window owner) {
        show(owner, YearMonth.now());
    }

    public void show(Window owner, YearMonth initialMonth) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("月视图");
        dialog.setHeaderText("按月查看任务热度，并可点击具体日期维护任务");
        applyStyles(dialog);
        dialog.getDialogPane().getButtonTypes().add(new ButtonType("关闭", ButtonBar.ButtonData.CANCEL_CLOSE));

        AtomicReference<YearMonth> currentMonth = new AtomicReference<>(initialMonth == null ? YearMonth.now() : initialMonth);
        DailyAgendaDialog agendaDialog = new DailyAgendaDialog(scheduleService, onTasksChanged);

        Label monthLabel = new Label();
        monthLabel.getStyleClass().add("headline-small");
        Label summaryLabel = new Label();
        summaryLabel.getStyleClass().add("lead-small");

        Button previousButton = new Button("上月");
        previousButton.getStyleClass().add("secondary-button");
        Button nextButton = new Button("下月");
        nextButton.getStyleClass().add("secondary-button");

        GridPane grid = new GridPane();
        grid.getStyleClass().add("calendar-grid");
        grid.setHgap(10);
        grid.setVgap(10);

        Runnable render = () -> renderMonth(dialog, grid, monthLabel, summaryLabel, currentMonth.get(), agendaDialog);
        previousButton.setOnAction(event -> {
            currentMonth.set(currentMonth.get().minusMonths(1));
            render.run();
        });
        nextButton.setOnAction(event -> {
            currentMonth.set(currentMonth.get().plusMonths(1));
            render.run();
        });

        Region spacer = new Region();
        javafx.scene.layout.HBox.setHgrow(spacer, Priority.ALWAYS);
        javafx.scene.layout.HBox toolbar = new javafx.scene.layout.HBox(10, previousButton, nextButton, spacer, monthLabel);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        Label hintLabel = new Label("点击具体日期可查看当天日程并直接新建、编辑或删除任务。色块越深，代表当天任务越多。");
        hintLabel.getStyleClass().add("hint-text");
        hintLabel.setWrapText(true);

        VBox root = new VBox(16, toolbar, summaryLabel, grid, hintLabel);
        root.setPadding(new Insets(12, 0, 0, 0));
        dialog.getDialogPane().setContent(root);

        render.run();
        dialog.showAndWait();
    }

    private void renderMonth(
            Dialog<?> dialog,
            GridPane grid,
            Label monthLabel,
            Label summaryLabel,
            YearMonth month,
            DailyAgendaDialog agendaDialog) {
        grid.getChildren().clear();
        grid.getColumnConstraints().clear();

        for (int columnIndex = 0; columnIndex < 7; columnIndex++) {
            ColumnConstraints constraints = new ColumnConstraints(140);
            constraints.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(constraints);
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

        Map<LocalDate, Integer> counts = scheduleService.buildMonthTaskCounts(month);
        int maxCount = counts.values().stream().max(Comparator.naturalOrder()).orElse(0);
        int totalCount = counts.values().stream().mapToInt(Integer::intValue).sum();

        monthLabel.setText(month.getYear() + " 年 " + month.getMonthValue() + " 月");
        summaryLabel.setText("本月累计 " + totalCount + " 项任务，最高单日 " + maxCount + " 项。DDL 统计已合并进每日总数。");

        int firstDayColumn = month.atDay(1).getDayOfWeek().getValue() - 1;
        int dayOfMonth = 1;
        LocalDate today = LocalDate.now();

        for (int weekIndex = 0; weekIndex < 6; weekIndex++) {
            for (int dayIndex = 0; dayIndex < 7; dayIndex++) {
                int cellIndex = weekIndex * 7 + dayIndex;
                if (cellIndex < firstDayColumn || dayOfMonth > month.lengthOfMonth()) {
                    Region placeholder = new Region();
                    placeholder.getStyleClass().add("calendar-cell-blank");
                    placeholder.setMinSize(140, 96);
                    grid.add(placeholder, dayIndex, weekIndex + 1);
                    continue;
                }

                LocalDate date = month.atDay(dayOfMonth++);
                int taskCount = counts.getOrDefault(date, 0);
                int ddlCount = scheduleService.listDeadlinesForDate(date).size();

                Label dayLabel = new Label(Integer.toString(date.getDayOfMonth()));
                dayLabel.getStyleClass().add("calendar-day-number");

                Label countLabel = new Label(taskCount == 0 ? "空白日" : taskCount + " 项安排");
                countLabel.getStyleClass().add("calendar-day-summary");

                Label ddlLabel = new Label(ddlCount == 0 ? "无 DDL" : "DDL " + ddlCount);
                ddlLabel.getStyleClass().add("calendar-day-deadline");

                VBox cell = new VBox(8, dayLabel, countLabel, ddlLabel);
                cell.getStyleClass().add("calendar-cell");
                if (date.equals(today)) {
                    cell.getStyleClass().add("calendar-cell-today");
                }
                cell.setPadding(new Insets(12));
                cell.setMinSize(140, 96);
                cell.setStyle(colorGradientService.backgroundStyle(taskCount, maxCount)
                        + " -fx-background-radius: 18px;"
                        + " -fx-border-radius: 18px;");
                cell.setOnMouseClicked(event -> {
                    agendaDialog.show(dialog.getDialogPane().getScene().getWindow(), date);
                    onTasksChanged.run();
                    renderMonth(dialog, grid, monthLabel, summaryLabel, month, agendaDialog);
                });
                grid.add(cell, dayIndex, weekIndex + 1);
            }
        }
    }

    private void applyStyles(Dialog<?> dialog) {
        URL stylesheet = Thread.currentThread().getContextClassLoader().getResource("css/app.css");
        if (stylesheet != null) {
            dialog.getDialogPane().getStylesheets().add(stylesheet.toExternalForm());
        }
        dialog.getDialogPane().getStyleClass().add("app-dialog");
    }
}