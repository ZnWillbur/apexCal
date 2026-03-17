package com.apexcal.application.service;

import com.apexcal.infrastructure.config.AppDirectories;
import com.apexcal.infrastructure.persistence.ConfigRepository;
import com.apexcal.infrastructure.persistence.DatabaseManager;
import com.apexcal.infrastructure.persistence.SQLiteConfigRepository;
import com.apexcal.infrastructure.windows.WindowsStartupIntegration;
import java.nio.file.Path;

public final class AppConfigService {
    private static final String STARTUP_KEY = "app.startup.enabled";

    private final ConfigRepository configRepository;
    private final WindowsStartupIntegration startupIntegration;

    public AppConfigService() {
        this(AppDirectories.dataDirectory());
    }

    public AppConfigService(Path dataDirectory) {
        DatabaseManager databaseManager = new DatabaseManager(dataDirectory);
        databaseManager.initialize();
        this.configRepository = new SQLiteConfigRepository(databaseManager);
        this.startupIntegration = new WindowsStartupIntegration();
    }

    public boolean isStartupEnabled() {
        boolean stored = Boolean.parseBoolean(configRepository.findValue(STARTUP_KEY).orElse("false"));
        if (!startupIntegration.isSupported()) {
            return stored;
        }
        boolean actual = startupIntegration.isEnabled();
        if (actual != stored) {
            configRepository.putValue(STARTUP_KEY, Boolean.toString(actual));
        }
        return actual;
    }

    public void setStartupEnabled(boolean enabled) {
        if (startupIntegration.isSupported()) {
            boolean actual = startupIntegration.isEnabled();
            if (actual != enabled) {
                startupIntegration.setEnabled(enabled);
            }
        }
        configRepository.putValue(STARTUP_KEY, Boolean.toString(enabled));
    }
}