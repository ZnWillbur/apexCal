package com.apexcal.domain.service;

import java.util.Locale;
import javafx.scene.paint.Color;

public final class ColorGradientService {
    private static final Color END_COLOR = Color.web("#8F1F1B");

    public Color colorForCount(int count, int max) {
        if (count <= 0 || max <= 0) {
            return Color.rgb(255, 255, 255, 0.0);
        }
        double ratio = Math.min(1.0, (double) count / max);
        double red = interpolate(1.0, END_COLOR.getRed(), ratio);
        double green = interpolate(1.0, END_COLOR.getGreen(), ratio);
        double blue = interpolate(1.0, END_COLOR.getBlue(), ratio);
        double alpha = 0.14 + 0.82 * ratio;
        return new Color(red, green, blue, alpha);
    }

    public String backgroundStyle(int count, int max) {
        Color color = colorForCount(count, max);
        int red = (int) Math.round(color.getRed() * 255);
        int green = (int) Math.round(color.getGreen() * 255);
        int blue = (int) Math.round(color.getBlue() * 255);
        return String.format(Locale.ROOT, "-fx-background-color: rgba(%d, %d, %d, %.3f);", red, green, blue, color.getOpacity());
    }

    private double interpolate(double start, double end, double ratio) {
        return start + (end - start) * ratio;
    }
}