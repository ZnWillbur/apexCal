package com.apexcal.presentation.tray;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import javafx.application.Platform;

public final class AppTrayManager {
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

            MenuItem openItem = new MenuItem("打开主界面");
            openItem.addActionListener(event -> Platform.runLater(openMainAction));
            menu.add(openItem);

            MenuItem newTaskItem = new MenuItem("新建任务");
            newTaskItem.addActionListener(event -> Platform.runLater(newTaskAction));
            menu.add(newTaskItem);

            MenuItem widgetItem = new MenuItem("显示 / 隐藏小窗");
            widgetItem.addActionListener(event -> Platform.runLater(toggleWidgetAction));
            menu.add(widgetItem);

            menu.addSeparator();

            MenuItem exitItem = new MenuItem("退出程序");
            exitItem.addActionListener(event -> Platform.runLater(exitAction));
            menu.add(exitItem);

            trayIcon = new TrayIcon(createTrayImage(), "ApexCal", menu);
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
            trayIcon.displayMessage("ApexCal", "主窗口已隐藏到系统托盘", TrayIcon.MessageType.INFO);
        }
    }

    public void dispose() {
        if (trayIcon != null) {
            SystemTray.getSystemTray().remove(trayIcon);
            trayIcon = null;
        }
    }

    private Image createTrayImage() {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(new Color(180, 70, 60));
        graphics.fillRoundRect(0, 0, 32, 32, 10, 10);
        graphics.setColor(Color.WHITE);
        graphics.setFont(new Font("Segoe UI", Font.BOLD, 16));
        graphics.drawString("A", 10, 22);
        graphics.dispose();
        return image;
    }
}