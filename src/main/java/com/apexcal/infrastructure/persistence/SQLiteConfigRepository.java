package com.apexcal.infrastructure.persistence;

import com.apexcal.domain.layout.TimeSection;
import com.apexcal.domain.semester.SemesterConfig;
import com.apexcal.infrastructure.json.ObjectMapperFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SQLiteConfigRepository implements ConfigRepository {
    private final DatabaseManager databaseManager;
    private final ObjectMapper objectMapper = ObjectMapperFactory.create();

    public SQLiteConfigRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public Optional<SemesterConfig> loadSemesterConfig() {
        String sql = """
                SELECT semester_name, first_monday, total_weeks, week_view_days, schedule_template_json
                FROM semester_config
                WHERE config_id = 1
                """;
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                return Optional.empty();
            }

            return Optional.of(new SemesterConfig(
                    resultSet.getString("semester_name"),
                    LocalDate.parse(resultSet.getString("first_monday")),
                    resultSet.getInt("total_weeks"),
                    resultSet.getInt("week_view_days"),
                    readSections(resultSet.getString("schedule_template_json"))));
        } catch (SQLException exception) {
            throw new IllegalStateException("无法读取学期配置", exception);
        }
    }

    @Override
    public void saveSemesterConfig(SemesterConfig config) {
        String sql = """
                INSERT INTO semester_config(
                    config_id, semester_name, first_monday, total_weeks, week_view_days,
                    schedule_template_json, created_at, updated_at
                ) VALUES(1, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(config_id) DO UPDATE SET
                    semester_name = excluded.semester_name,
                    first_monday = excluded.first_monday,
                    total_weeks = excluded.total_weeks,
                    week_view_days = excluded.week_view_days,
                    schedule_template_json = excluded.schedule_template_json,
                    updated_at = excluded.updated_at
                """;
        String now = LocalDateTime.now().toString();
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, config.semesterName());
            statement.setString(2, config.firstMonday().toString());
            statement.setInt(3, config.totalWeeks());
            statement.setInt(4, config.weekViewDays());
            statement.setString(5, writeSections(config.sections()));
            statement.setString(6, now);
            statement.setString(7, now);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("无法保存学期配置", exception);
        }
    }

    @Override
    public Optional<String> findValue(String key) {
        String sql = "SELECT config_value FROM app_config WHERE config_key = ?";
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.ofNullable(resultSet.getString("config_value"));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("无法读取应用配置", exception);
        }
    }

    @Override
    public void putValue(String key, String value) {
        String sql = """
                INSERT INTO app_config(config_key, config_value, updated_at)
                VALUES(?, ?, ?)
                ON CONFLICT(config_key) DO UPDATE SET
                    config_value = excluded.config_value,
                    updated_at = excluded.updated_at
                """;
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key);
            statement.setString(2, value);
            statement.setString(3, LocalDateTime.now().toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("无法写入应用配置", exception);
        }
    }

    private List<TimeSection> readSections(String rawJson) {
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            List<TimeSection> sections = new ArrayList<>();
            for (JsonNode node : root.path("sections")) {
                sections.add(new TimeSection(
                        node.path("section").asInt(),
                        LocalTime.parse(node.path("start").asText()),
                        LocalTime.parse(node.path("end").asText())));
            }
            return sections;
        } catch (Exception exception) {
            throw new IllegalStateException("无法解析节次模板", exception);
        }
    }

    private String writeSections(List<TimeSection> sections) {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode arrayNode = root.putArray("sections");
        for (TimeSection section : sections) {
            ObjectNode sectionNode = arrayNode.addObject();
            sectionNode.put("section", section.section());
            sectionNode.put("start", section.start().toString());
            sectionNode.put("end", section.end().toString());
        }
        return root.toString();
    }
}