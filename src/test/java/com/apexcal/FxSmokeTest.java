package com.apexcal;

import com.apexcal.application.service.AppConfigService;
import com.apexcal.application.service.ScheduleService;
import com.apexcal.presentation.controller.MainWindowController;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FxSmokeTest {
    @TempDir
    Path tempDir;

    @BeforeAll
    static void initToolkit() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException ignored) {
            latch.countDown();
        }
        Assertions.assertTrue(latch.await(10, TimeUnit.SECONDS));
    }

    @Test
    void shouldLoadAndInitializeMainWindow() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-window.fxml"));
                Parent root = loader.load();
                MainWindowController controller = loader.getController();
                controller.init(new ScheduleService(tempDir), new AppConfigService(tempDir), () -> {
                }, () -> {
                });
                Assertions.assertNotNull(root);
            } catch (IOException | RuntimeException exception) {
                failure.set(exception);
            } finally {
                latch.countDown();
            }
        });

        Assertions.assertTrue(latch.await(10, TimeUnit.SECONDS));
        if (failure.get() != null) {
            Assertions.fail(failure.get());
        }
    }
}