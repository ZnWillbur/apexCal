package com.apexcal.bootstrap;

import javafx.application.Application;

public final class AppLauncher {
    private AppLauncher() {
    }

    public static void main(String[] args) {
        Application.launch(ApexCalApplication.class, args);
    }
}
