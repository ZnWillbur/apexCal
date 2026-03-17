package com.apexcal.presentation.widget;

import com.apexcal.application.service.ScheduleService;
import com.apexcal.domain.task.TaskItem;
import com.apexcal.domain.task.TaskOccurrence;
import com.apexcal.presentation.window.AppIconFactory;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public final class DesktopWidgetManager {
    private final ScheduleService scheduleService;
    private final Runnable openMainAction;
    private final Runnable newTaskAction;
    private final Runnable exitAction;

    private Stage stage;
    private VBox listBox;
    private ScrollPane listScrollPane;
    private Label summaryLabel;
    private boolean suppressed;
    private double dragOffsetX;
    private double dragOffsetY;

    public DesktopWidgetManager(ScheduleService scheduleService, Runnable openMainAction, Runnable newTaskAction, Runnable exitAction) {
        this.scheduleService = scheduleService;
        this.openMainAction = openMainAction;
        this.newTaskAction = newTaskAction;
        this.exitAction = exitAction;
    }

    public void show() {
        suppressed = false;
        ensureStage();
        refresh();
        if (!stage.isShowing()) {
            stage.show();
            stage.toBack();
            positionTopRight();
        }
    }

    public void showAfterMainClose() {
        if (!suppressed) {
            show();
        }
    }

    public void hide() {
        if (stage != null) {
            stage.hide();
        }
    }

    public void suppress() {
        suppressed = true;
        hide();
    }

    public void toggle() {
        if (stage != null && stage.isShowing()) {
            suppress();
        } else {
            show();
        }
    }

    public void refresh() {
        if (listBox == null || summaryLabel == null) {
            return;
        }
        LocalDate today = LocalDate.now();
        listBox.getChildren().clear();

        var occurrences = scheduleService.listOccurrencesForDate(today);
        var deadlines = scheduleService.listDeadlinesForDate(today);
        summaryLabel.setText(scheduleService.buildTodaySummary(today).courseSummary() + " · "
                + scheduleService.buildTodaySummary(today).customSummary() + " · "
                + scheduleService.buildTodaySummary(today).deadlineSummary());

        if (occurrences.isEmpty() && deadlines.isEmpty()) {
            Label emptyLabel = new Label("今天没有安排，先休息一下。\n右键可打开主界面或新建任务。");
            emptyLabel.getStyleClass().add("widget-empty");
            listBox.getChildren().add(emptyLabel);
            return;
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
        for (TaskOccurrence occurrence : occurrences) {
            Label line = new Label(occurrence.task().startTime().format(formatter)
                    + " - " + occurrence.task().endTime().format(formatter)
                    + "  " + occurrence.task().title());
            line.getStyleClass().add("widget-line");
            listBox.getChildren().add(line);
        }
        for (TaskItem deadline : deadlines) {
            Label line = new Label("DDL " + deadline.dueAt().toLocalTime().format(formatter) + "  " + deadline.title());
            line.getStyleClass().add("widget-line");
            listBox.getChildren().add(line);
        }
    }

    public void dispose() {
        if (stage != null) {
            stage.close();
            stage = null;
        }
    }

    private void ensureStage() {
        if (stage != null) {
            return;
        }

        Label titleLabel = new Label("今日日程");
        titleLabel.getStyleClass().add("widget-title");
        summaryLabel = new Label();
        summaryLabel.getStyleClass().add("widget-summary");

        listBox = new VBox(8);
        listScrollPane = new ScrollPane(listBox);
        listScrollPane.setFitToWidth(true);
        listScrollPane.setPannable(true);
        listScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        listScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        listScrollPane.setPrefViewportHeight(152);
        listScrollPane.getStyleClass().add("widget-scroll");

        VBox root = new VBox(12, titleLabel, summaryLabel, listScrollPane);
        root.getStyleClass().add("widget-card");
        root.setPadding(new Insets(16));

        Scene scene = new Scene(root, 340, 260);
        scene.setFill(Color.TRANSPARENT);
        URL stylesheet = Thread.currentThread().getContextClassLoader().getResource("css/app.css");
        if (stylesheet != null) {
            scene.getStylesheets().add(stylesheet.toExternalForm());
        }

        ContextMenu contextMenu = new ContextMenu();
        MenuItem openItem = new MenuItem("Open Main Window");
        openItem.setOnAction(event -> openMainAction.run());
        MenuItem newTaskItem = new MenuItem("New Task");
        newTaskItem.setOnAction(event -> newTaskAction.run());
        MenuItem closeWidgetItem = new MenuItem("Close Widget");
        closeWidgetItem.setOnAction(event -> suppress());
        MenuItem exitItem = new MenuItem("Exit");
        exitItem.setOnAction(event -> exitAction.run());
        contextMenu.getItems().addAll(openItem, newTaskItem, closeWidgetItem, exitItem);

        root.setOnContextMenuRequested(event -> contextMenu.show(root, event.getScreenX(), event.getScreenY()));
        root.setOnMousePressed(event -> {
            dragOffsetX = event.getSceneX();
            dragOffsetY = event.getSceneY();
        });
        root.setOnMouseDragged(event -> {
            stage.setX(event.getScreenX() - dragOffsetX);
            stage.setY(event.getScreenY() - dragOffsetY);
        });

        stage = new Stage(StageStyle.TRANSPARENT);
        stage.setScene(scene);
        stage.getIcons().setAll(AppIconFactory.fxIcon());
        stage.setResizable(false);
        stage.setAlwaysOnTop(false);
    }

    private void positionTopRight() {
        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        stage.setX(bounds.getMaxX() - stage.getWidth() - 36);
        stage.setY(bounds.getMinY() + 36);
    }
}