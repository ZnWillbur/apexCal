package com.apexcal.presentation.viewmodel;

import com.apexcal.application.service.TodaySummary;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public final class TodaySummaryViewModel {
    private final StringProperty courseSummary = new SimpleStringProperty("课程：等待导入课表");
    private final StringProperty customSummary = new SimpleStringProperty("自建：0 项");
    private final StringProperty deadlineSummary = new SimpleStringProperty("DDL：0 项");

    public void update(TodaySummary summary) {
        courseSummary.set(summary.courseSummary());
        customSummary.set(summary.customSummary());
        deadlineSummary.set(summary.deadlineSummary());
    }

    public StringProperty courseSummaryProperty() {
        return courseSummary;
    }

    public StringProperty customSummaryProperty() {
        return customSummary;
    }

    public StringProperty deadlineSummaryProperty() {
        return deadlineSummary;
    }
}

