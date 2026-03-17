package com.apexcal.presentation.window;

import java.net.URL;
import javafx.scene.control.Alert;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

public final class WindowTheme {
    private static final String APP_DIALOG_CLASS = "app-dialog";
    private static final Image APP_ICON = AppIconFactory.fxIcon();

    private WindowTheme() {
    }

    public static void applyPrimaryStage(Stage stage) {
        try {
            stage.initStyle(StageStyle.UNIFIED);
        } catch (IllegalStateException ignored) {
        }
        applyIcon(stage);
    }

    public static void applyDialogTheme(Dialog<?> dialog) {
        try {
            dialog.initStyle(StageStyle.UNIFIED);
        } catch (IllegalStateException ignored) {
        }
        applyDialogPaneTheme(dialog.getDialogPane());
        dialog.setOnShown(event -> applyIcon(dialog.getDialogPane().getScene().getWindow()));
    }

    public static void applyAlertTheme(Alert alert) {
        applyDialogTheme(alert);
    }

    private static void applyDialogPaneTheme(DialogPane dialogPane) {
        URL stylesheet = Thread.currentThread().getContextClassLoader().getResource("css/app.css");
        if (stylesheet != null) {
            String stylesheetPath = stylesheet.toExternalForm();
            if (!dialogPane.getStylesheets().contains(stylesheetPath)) {
                dialogPane.getStylesheets().add(stylesheetPath);
            }
        }
        if (!dialogPane.getStyleClass().contains(APP_DIALOG_CLASS)) {
            dialogPane.getStyleClass().add(APP_DIALOG_CLASS);
        }
    }

    private static void applyIcon(Window window) {
        if (window instanceof Stage stage) {
            applyIcon(stage);
        }
    }

    private static void applyIcon(Stage stage) {
        if (stage.getIcons().isEmpty()) {
            stage.getIcons().add(APP_ICON);
        }
    }
}