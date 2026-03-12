package com.apexcal.presentation.controller;

import com.apexcal.application.service.TodaySummary;
import com.apexcal.presentation.viewmodel.TodaySummaryViewModel;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

public final class WelcomeController {
    @FXML
    private Label courseSummaryLabel;

    @FXML
    private Label customSummaryLabel;

    @FXML
    private Label deadlineSummaryLabel;

    private final TodaySummaryViewModel viewModel = new TodaySummaryViewModel();
    private Runnable onStart = () -> {
    };
    private Runnable onExit = () -> {
    };

    @FXML
    private void initialize() {
        courseSummaryLabel.textProperty().bind(viewModel.courseSummaryProperty());
        customSummaryLabel.textProperty().bind(viewModel.customSummaryProperty());
        deadlineSummaryLabel.textProperty().bind(viewModel.deadlineSummaryProperty());
    }

    public void initActions(Runnable onStart, Runnable onExit) {
        this.onStart = onStart;
        this.onExit = onExit;
    }

    public void setSummary(TodaySummary summary) {
        viewModel.update(summary);
    }

    @FXML
    private void handleStartAction() {
        onStart.run();
    }

    @FXML
    private void handleExitAction() {
        onExit.run();
    }
}

