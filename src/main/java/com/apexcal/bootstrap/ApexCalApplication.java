package com.apexcal.bootstrap;

import com.apexcal.application.service.AppConfigService;
import com.apexcal.application.service.ScheduleService;
import com.apexcal.infrastructure.config.AppMetadata;
import com.apexcal.presentation.controller.MainWindowController;
import com.apexcal.presentation.controller.WelcomeController;
import com.apexcal.presentation.tray.AppTrayManager;
import com.apexcal.presentation.widget.DesktopWidgetManager;
import java.io.IOException;
import java.net.URL;
import java.time.LocalDate;
import java.util.Objects;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

public final class ApexCalApplication extends Application {
    private final ScheduleService scheduleService = new ScheduleService();
    private final AppConfigService appConfigService = new AppConfigService();
    private final DesktopWidgetManager widgetManager = new DesktopWidgetManager(
            scheduleService,
            this::openOrShowMainWindow,
            this::openNewTaskFromTray,
            this::exitApplication);
    private final AppTrayManager trayManager = new AppTrayManager(
            this::openOrShowMainWindow,
            this::openNewTaskFromTray,
            widgetManager::toggle,
            this::exitApplication);

    private Stage primaryStage;
    private MainWindowController mainWindowController;
    private boolean trayInstalled;
    private boolean shuttingDown;

    @Override
    public void start(Stage primaryStage) throws IOException {
        this.primaryStage = primaryStage;
        Platform.setImplicitExit(false);
        trayInstalled = trayManager.install();
        primaryStage.setOnCloseRequest(this::handleStageCloseRequest);
        showWelcome(primaryStage);
        widgetManager.show();
    }

    private void showWelcome(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(resource("fxml/welcome.fxml"));
        Scene scene = new Scene(loader.load(), 1100, 720);
        scene.getStylesheets().add(resource("css/app.css").toExternalForm());

        WelcomeController controller = loader.getController();
        controller.initActions(() -> openMainWindow(stage), this::exitApplication);
        controller.setSummary(scheduleService.buildTodaySummary(LocalDate.now()));

        stage.setTitle(AppMetadata.DISPLAY_NAME);
        stage.setMinWidth(960);
        stage.setMinHeight(640);
        stage.setScene(scene);
        mainWindowController = null;
        stage.show();
    }

    private void openMainWindow(Stage stage) {
        try {
            FXMLLoader loader = new FXMLLoader(resource("fxml/main-window.fxml"));
            Scene scene = new Scene(loader.load(), 1440, 920);
            scene.getStylesheets().add(resource("css/app.css").toExternalForm());

            MainWindowController controller = loader.getController();
            controller.init(scheduleService, appConfigService, () -> reopenWelcome(stage), widgetManager::refresh);
            mainWindowController = controller;

            stage.setScene(scene);
            stage.setMinWidth(1180);
            stage.setMinHeight(760);
            stage.show();
        } catch (IOException exception) {
            showError("无法打开主界面", exception);
        }
    }

    private void reopenWelcome(Stage stage) {
        try {
            showWelcome(stage);
        } catch (IOException exception) {
            showError("无法返回欢迎页", exception);
        }
    }

    private void handleStageCloseRequest(WindowEvent event) {
        if (shuttingDown) {
            return;
        }
        if (trayInstalled) {
            event.consume();
            primaryStage.hide();
            widgetManager.showAfterMainClose();
            trayManager.notifyHidden();
            return;
        }
        exitApplication();
    }

    private void openOrShowMainWindow() {
        if (primaryStage == null) {
            return;
        }
        try {
            if (mainWindowController == null) {
                openMainWindow(primaryStage);
            } else {
                primaryStage.show();
                primaryStage.toFront();
                primaryStage.requestFocus();
            }
        } catch (Exception exception) {
            showError("无法打开主界面", exception instanceof IOException ioException ? ioException : new IOException(exception));
        }
    }

    private void openNewTaskFromTray() {
        openOrShowMainWindow();
        if (mainWindowController != null) {
            mainWindowController.openNewTaskDialog(com.apexcal.domain.task.TaskType.CUSTOM, LocalDate.now());
        }
    }

    private void exitApplication() {
        shuttingDown = true;
        trayManager.dispose();
        widgetManager.dispose();
        if (primaryStage != null) {
            primaryStage.close();
        }
        Platform.exit();
    }

    @Override
    public void stop() {
        trayManager.dispose();
        widgetManager.dispose();
    }

    private void showError(String header, Exception exception) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(AppMetadata.DISPLAY_NAME);
        alert.setHeaderText(header);
        alert.setContentText(exception.getMessage());
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        alert.showAndWait();
    }

    private URL resource(String path) {
        return Objects.requireNonNull(
                Thread.currentThread().getContextClassLoader().getResource(path),
                () -> "Missing resource: " + path);
    }
}

