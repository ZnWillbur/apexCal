package com.apexcal.presentation.viewmodel;

import com.apexcal.application.service.ScheduleService;
import com.apexcal.domain.semester.SemesterConfig;
import com.apexcal.domain.semester.WeekSchedule;
import com.apexcal.domain.service.WeekCalculationService;
import java.time.LocalDate;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public final class WeekViewModel {
    private final ScheduleService scheduleService;
    private final WeekCalculationService weekCalculationService = new WeekCalculationService();
    private final StringProperty weekLabel = new SimpleStringProperty();
    private final StringProperty rangeLabel = new SimpleStringProperty();
    private final StringProperty sourceLabel = new SimpleStringProperty();
    private LocalDate anchorDate = LocalDate.now();
    private WeekSchedule currentWeekSchedule;

    public WeekViewModel(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
        refresh();
    }

    public void previousWeek() {
        anchorDate = anchorDate.minusWeeks(1);
        refresh();
    }

    public void nextWeek() {
        anchorDate = anchorDate.plusWeeks(1);
        refresh();
    }

    public void refresh() {
        SemesterConfig config = scheduleService.getSemesterConfig();
        currentWeekSchedule = scheduleService.buildWeekSchedule(anchorDate, config.weekViewDays());
        weekLabel.set(weekCalculationService.formatWeekLabel(currentWeekSchedule.weekNumber()));
        rangeLabel.set(weekCalculationService.formatRange(currentWeekSchedule.visibleDates()));
        sourceLabel.set(scheduleService.buildSourceSummary() + " · 数据源 config/import/class.json / time.json");
    }

    public WeekSchedule currentWeekSchedule() {
        return currentWeekSchedule;
    }

    public SemesterConfig semesterConfig() {
        return scheduleService.getSemesterConfig();
    }

    public StringProperty weekLabelProperty() {
        return weekLabel;
    }

    public StringProperty rangeLabelProperty() {
        return rangeLabel;
    }

    public StringProperty sourceLabelProperty() {
        return sourceLabel;
    }
}
