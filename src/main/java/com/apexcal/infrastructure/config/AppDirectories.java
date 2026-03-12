package com.apexcal.infrastructure.config;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class AppDirectories {
    private AppDirectories() {
    }

    public static Path baseDirectory() {
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData == null || localAppData.isBlank()) {
            return Paths.get(System.getProperty("user.home"), "AppData", "Local", AppMetadata.APP_NAME);
        }
        return Paths.get(localAppData, AppMetadata.APP_NAME);
    }

    public static Path dataDirectory() {
        return baseDirectory().resolve("data");
    }

    public static Path logDirectory() {
        return baseDirectory().resolve("logs");
    }
}
