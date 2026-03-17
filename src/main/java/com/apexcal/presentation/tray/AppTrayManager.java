package com.apexcal.presentation.tray;

import java.awt.Font;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import com.apexcal.presentation.window.AppIconFactory;
import javafx.application.Platform;

public final class AppTrayManager {
    private static final Font TRAY_MENU_FONT = new Font("Microsoft YaHei UI", Font.PLAIN, 13);
    private static final String APP_NAME = "ApexCal";
    private static final String OPEN_MAIN_TEXT = "Open Main Window";
    private static final String NEW_TASK_TEXT = "New Task";
    private static final String TOGGLE_WIDGET_TEXT = "Toggle Widget";
    private static final String EXIT_TEXT = "Exit";
    private static final String HIDDEN_MESSAGE_TEXT = "Main window has been hidden to tray";

    private final Runnable openMainAction;
    private final Runnable newTaskAction;
    private final Runnable toggleWidgetAction;
    private final Runnable exitAction;
    private TrayIcon trayIcon;

    public AppTrayManager(Runnable openMainAction, Runnable newTaskAction, Runnable toggleWidgetAction, Runnable exitAction) {
        this.openMainAction = openMainAction;
        this.newTaskAction = newTaskAction;
        this.toggleWidgetAction = toggleWidgetAction;
        this.exitAction = exitAction;
    }

    public boolean install() {
        if (!SystemTray.isSupported() || trayIcon != null) {
            return trayIcon != null;
        }
        try {
            PopupMenu menu = new PopupMenu();
            menu.setFont(TRAY_MENU_FONT);

            MenuItem openItem = newMenuItem(OPEN_MAIN_TEXT, openMainAction);
            menu.add(openItem);

            MenuItem newTaskItem = newMenuItem(NEW_TASK_TEXT, newTaskAction);
            menu.add(newTaskItem);

            MenuItem widgetItem = newMenuItem(TOGGLE_WIDGET_TEXT, toggleWidgetAction);
            menu.add(widgetItem);

            menu.addSeparator();

            MenuItem exitItem = newMenuItem(EXIT_TEXT, exitAction);
            menu.add(exitItem);

            trayIcon = new TrayIcon(createTrayImage(), APP_NAME, menu);
            trayIcon.setImageAutoSize(true);
            trayIcon.addActionListener(event -> Platform.runLater(openMainAction));
            SystemTray.getSystemTray().add(trayIcon);
            return true;
        } catch (Exception exception) {
            trayIcon = null;
            return false;
        }
    }

    public void notifyHidden() {
        if (trayIcon != null) {
            trayIcon.displayMessage(APP_NAME, HIDDEN_MESSAGE_TEXT, TrayIcon.MessageType.INFO);
        }
    }

    public void dispose() {
        if (trayIcon != null) {
            SystemTray.getSystemTray().remove(trayIcon);
            trayIcon = null;
        }
    }

    private Image createTrayImage() {
        return AppIconFactory.awtIcon(32);
    }

    private MenuItem newMenuItem(String title, Runnable action) {
        MenuItem menuItem = new MenuItem(title);
        menuItem.setFont(TRAY_MENU_FONT);
        menuItem.addActionListener(event -> Platform.runLater(action));
        return menuItem;
    }
}