package com.apexcal.presentation.window;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

public final class AppIconFactory {
    private static final BufferedImage BASE_AWT_ICON = createBufferedIcon(64);
    private static final Image BASE_FX_ICON = SwingFXUtils.toFXImage(BASE_AWT_ICON, null);

    private AppIconFactory() {
    }

    public static Image fxIcon() {
        return BASE_FX_ICON;
    }

    public static java.awt.Image awtIcon(int size) {
        if (size <= 0) {
            return BASE_AWT_ICON;
        }
        if (size == BASE_AWT_ICON.getWidth()) {
            return BASE_AWT_ICON;
        }
        BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaled.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.drawImage(BASE_AWT_ICON, 0, 0, size, size, null);
        graphics.dispose();
        return scaled;
    }

    private static BufferedImage createBufferedIcon(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        graphics.setColor(new Color(166, 63, 52));
        graphics.fillRoundRect(0, 0, size, size, size / 3, size / 3);

        graphics.setColor(new Color(255, 255, 255, 42));
        graphics.fillRoundRect(4, 4, size - 8, (int) (size * 0.42), size / 3, size / 3);

        graphics.setColor(Color.WHITE);
        graphics.setFont(new Font("Segoe UI", Font.BOLD, (int) (size * 0.56)));
        int baselineY = (int) (size * 0.72);
        int baselineX = (int) (size * 0.24);
        graphics.drawString("A", baselineX, baselineY);

        graphics.dispose();
        return image;
    }
}