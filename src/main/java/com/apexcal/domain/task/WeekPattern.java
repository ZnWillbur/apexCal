package com.apexcal.domain.task;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class WeekPattern {
    private final String raw;
    private final List<Range> ranges;

    private WeekPattern(String raw, List<Range> ranges) {
        this.raw = raw;
        this.ranges = List.copyOf(ranges);
    }

    public static WeekPattern parse(String raw) {
        String normalized = Objects.requireNonNullElse(raw, "").trim();
        if (normalized.isBlank()) {
            return new WeekPattern("", List.of());
        }

        List<Range> parsedRanges = new ArrayList<>();
        String[] parts = normalized.replace('，', ',').split(",");
        for (String part : parts) {
            String token = part.trim();
            if (token.isEmpty()) {
                continue;
            }

            if (token.contains("-")) {
                String[] boundaries = token.split("-");
                if (boundaries.length == 2) {
                    int start = Integer.parseInt(boundaries[0].trim());
                    int end = Integer.parseInt(boundaries[1].trim());
                    parsedRanges.add(new Range(Math.min(start, end), Math.max(start, end)));
                }
            } else {
                int value = Integer.parseInt(token);
                parsedRanges.add(new Range(value, value));
            }
        }
        return new WeekPattern(normalized, parsedRanges);
    }

    public boolean contains(int weekNumber) {
        if (weekNumber < 1) {
            return false;
        }
        return ranges.stream().anyMatch(range -> range.contains(weekNumber));
    }

    @Override
    public String toString() {
        return raw;
    }

    private record Range(int startInclusive, int endInclusive) {
        private boolean contains(int value) {
            return value >= startInclusive && value <= endInclusive;
        }
    }
}
