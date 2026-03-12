package com.apexcal.infrastructure.persistence;

import com.apexcal.domain.task.TaskItem;
import com.apexcal.domain.task.TaskSource;
import com.apexcal.domain.task.TaskStatus;
import com.apexcal.domain.task.TaskType;
import com.apexcal.infrastructure.json.ObjectMapperFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SQLiteTaskRepository implements TaskRepository {
    private final DatabaseManager databaseManager;
    private final ObjectMapper objectMapper = ObjectMapperFactory.create();

    public SQLiteTaskRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public List<TaskItem> findAllActive() {
        String sql = baseSelectSql() + " WHERE t.deleted = 0 ORDER BY t.task_type, COALESCE(d.due_at, r.start_date, t.updated_at), t.title";
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            List<TaskItem> tasks = new ArrayList<>();
            while (resultSet.next()) {
                tasks.add(mapRow(resultSet));
            }
            return tasks;
        } catch (SQLException exception) {
            throw new IllegalStateException("无法读取任务列表", exception);
        }
    }

    @Override
    public Optional<TaskItem> findById(String uuid) {
        String sql = baseSelectSql() + " WHERE t.task_uuid = ? AND t.deleted = 0";
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRow(resultSet));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("无法读取任务", exception);
        }
    }

    @Override
    public TaskItem save(TaskItem task) {
        try (Connection connection = databaseManager.openConnection()) {
            connection.setAutoCommit(false);

            Optional<TaskItem> before = findByIdInternal(connection, task.uuid());
            if (before.isPresent()) {
                updateTaskRow(connection, task);
                deleteSchedules(connection, task.uuid());
            } else {
                insertTaskRow(connection, task);
            }

            insertSchedule(connection, task);
            insertHistory(connection, task.uuid(), before.isPresent() ? "UPDATE" : "CREATE", before.orElse(null), task);
            connection.commit();
            return task;
        } catch (SQLException exception) {
            throw new IllegalStateException("无法保存任务", exception);
        }
    }

    @Override
    public void softDelete(String uuid) {
        try (Connection connection = databaseManager.openConnection()) {
            connection.setAutoCommit(false);
            Optional<TaskItem> before = findByIdInternal(connection, uuid);
            if (before.isEmpty()) {
                connection.rollback();
                return;
            }

            TaskItem original = before.get();
            String sql = "UPDATE tasks SET deleted = 1, version = ?, updated_at = ? WHERE task_uuid = ?";
            LocalDateTime now = LocalDateTime.now();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, original.version() + 1);
                statement.setString(2, now.toString());
                statement.setString(3, uuid);
                statement.executeUpdate();
            }

            TaskItem after = new TaskItem(
                    original.uuid(),
                    original.type(),
                    original.source(),
                    original.title(),
                    original.note(),
                    original.location(),
                    original.colorHex(),
                    original.metadataJson(),
                    original.status(),
                    original.priority(),
                    original.weekday(),
                    original.startMinute(),
                    original.endMinute(),
                    original.weekPattern(),
                    original.startDate(),
                    original.endDate(),
                    original.dueAt(),
                    original.createdAt(),
                    now,
                    original.version() + 1);
            insertHistory(connection, uuid, "DELETE", original, after);
            connection.commit();
        } catch (SQLException exception) {
            throw new IllegalStateException("无法删除任务", exception);
        }
    }

    @Override
    public long countByType(TaskType type) {
        return countBy(type, null);
    }

    @Override
    public long countByTypeAndSource(TaskType type, TaskSource source) {
        return countBy(type, source);
    }

    @Override
    public void deleteByTypeAndSource(TaskType type, TaskSource source) {
        String sql = "DELETE FROM tasks WHERE task_type = ? AND source_tag = ?";
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, type.name());
            statement.setString(2, source.name());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("无法清理导入课程", exception);
        }
    }

    private long countBy(TaskType type, TaskSource source) {
        String sql = source == null
                ? "SELECT COUNT(*) FROM tasks WHERE task_type = ? AND deleted = 0"
                : "SELECT COUNT(*) FROM tasks WHERE task_type = ? AND source_tag = ? AND deleted = 0";
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, type.name());
            if (source != null) {
                statement.setString(2, source.name());
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return 0;
                }
                return resultSet.getLong(1);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("无法统计任务数量", exception);
        }
    }

    private String baseSelectSql() {
        return """
                SELECT t.task_uuid, t.task_type, t.source_tag, t.title, t.note, t.location, t.color_hex,
                       t.metadata_json, t.status, t.priority, t.created_at, t.updated_at, t.version,
                       r.weekday, r.start_minute, r.end_minute, r.week_pattern, r.start_date, r.end_date,
                       d.due_at
                FROM tasks t
                LEFT JOIN recurring_task_schedule r ON r.task_uuid = t.task_uuid
                LEFT JOIN deadline_task_schedule d ON d.task_uuid = t.task_uuid
                """;
    }

    private Optional<TaskItem> findByIdInternal(Connection connection, String uuid) throws SQLException {
        String sql = baseSelectSql() + " WHERE t.task_uuid = ? AND t.deleted = 0";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRow(resultSet));
            }
        }
    }

    private TaskItem mapRow(ResultSet resultSet) throws SQLException {
        int weekdayValue = resultSet.getInt("weekday");
        DayOfWeek weekday = resultSet.wasNull() ? null : DayOfWeek.of(weekdayValue);

        int startMinuteValue = resultSet.getInt("start_minute");
        Integer startMinute = resultSet.wasNull() ? null : startMinuteValue;

        int endMinuteValue = resultSet.getInt("end_minute");
        Integer endMinute = resultSet.wasNull() ? null : endMinuteValue;

        return new TaskItem(
                resultSet.getString("task_uuid"),
                TaskType.valueOf(resultSet.getString("task_type")),
                TaskSource.fromPersistence(resultSet.getString("source_tag")),
                resultSet.getString("title"),
                resultSet.getString("note"),
                resultSet.getString("location"),
                resultSet.getString("color_hex"),
                resultSet.getString("metadata_json"),
                TaskStatus.valueOf(resultSet.getString("status")),
                resultSet.getInt("priority"),
                weekday,
                startMinute,
                endMinute,
                nullableString(resultSet.getString("week_pattern")),
                nullableDate(resultSet.getString("start_date")),
                nullableDate(resultSet.getString("end_date")),
                nullableDateTime(resultSet.getString("due_at")),
                LocalDateTime.parse(resultSet.getString("created_at")),
                LocalDateTime.parse(resultSet.getString("updated_at")),
                resultSet.getInt("version"));
    }

    private void insertTaskRow(Connection connection, TaskItem task) throws SQLException {
        String sql = """
                INSERT INTO tasks(
                    task_uuid, task_type, source_tag, title, note, location, color_hex, metadata_json,
                    status, priority, created_at, updated_at, version, deleted, sync_state
                ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 'LOCAL_ONLY')
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindTaskRow(statement, task);
            statement.executeUpdate();
        }
    }

    private void updateTaskRow(Connection connection, TaskItem task) throws SQLException {
        String sql = """
                UPDATE tasks SET
                    task_type = ?,
                    source_tag = ?,
                    title = ?,
                    note = ?,
                    location = ?,
                    color_hex = ?,
                    metadata_json = ?,
                    status = ?,
                    priority = ?,
                    updated_at = ?,
                    version = ?
                WHERE task_uuid = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, task.type().name());
            statement.setString(2, task.source().name());
            statement.setString(3, task.title());
            statement.setString(4, task.note());
            statement.setString(5, task.location());
            statement.setString(6, task.colorHex());
            statement.setString(7, task.metadataJson());
            statement.setString(8, task.status().name());
            statement.setInt(9, task.priority());
            statement.setString(10, task.updatedAt().toString());
            statement.setInt(11, task.version());
            statement.setString(12, task.uuid());
            statement.executeUpdate();
        }
    }

    private void bindTaskRow(PreparedStatement statement, TaskItem task) throws SQLException {
        statement.setString(1, task.uuid());
        statement.setString(2, task.type().name());
        statement.setString(3, task.source().name());
        statement.setString(4, task.title());
        statement.setString(5, task.note());
        statement.setString(6, task.location());
        statement.setString(7, task.colorHex());
        statement.setString(8, task.metadataJson());
        statement.setString(9, task.status().name());
        statement.setInt(10, task.priority());
        statement.setString(11, task.createdAt().toString());
        statement.setString(12, task.updatedAt().toString());
        statement.setInt(13, task.version());
    }

    private void deleteSchedules(Connection connection, String taskUuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM recurring_task_schedule WHERE task_uuid = ?")) {
            statement.setString(1, taskUuid);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM deadline_task_schedule WHERE task_uuid = ?")) {
            statement.setString(1, taskUuid);
            statement.executeUpdate();
        }
    }

    private void insertSchedule(Connection connection, TaskItem task) throws SQLException {
        if (task.type() == TaskType.DDL) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO deadline_task_schedule(task_uuid, due_at) VALUES(?, ?)")) {
                statement.setString(1, task.uuid());
                statement.setString(2, task.dueAt().toString());
                statement.executeUpdate();
            }
            return;
        }

        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO recurring_task_schedule(
                    task_uuid, schedule_kind, weekday, start_minute, end_minute,
                    week_pattern, start_date, end_date, alignment_mode
                ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, task.uuid());
            statement.setString(2, task.type().name());
            statement.setInt(3, task.weekday().getValue());
            statement.setInt(4, task.startMinute());
            statement.setInt(5, task.endMinute());
            statement.setString(6, task.weekPattern().isBlank() ? null : task.weekPattern());
            statement.setString(7, task.startDate() == null ? null : task.startDate().toString());
            statement.setString(8, task.endDate() == null ? null : task.endDate().toString());
            statement.setString(9, task.type() == TaskType.COURSE ? "SECTION" : "FREEFORM");
            statement.executeUpdate();
        }
    }

    private void insertHistory(Connection connection, String taskUuid, String operation, TaskItem before, TaskItem after) throws SQLException {
        String sql = """
                INSERT INTO task_history(
                    task_uuid, operation, snapshot_before_json, snapshot_after_json, changed_at, sync_state
                ) VALUES(?, ?, ?, ?, ?, 'LOCAL_ONLY')
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, taskUuid);
            statement.setString(2, operation);
            statement.setString(3, serialize(before));
            statement.setString(4, serialize(after));
            statement.setString(5, LocalDateTime.now().toString());
            statement.executeUpdate();
        }
    }

    private String serialize(TaskItem task) {
        if (task == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(task);
        } catch (Exception exception) {
            throw new IllegalStateException("无法序列化任务快照", exception);
        }
    }

    private LocalDate nullableDate(String raw) {
        return raw == null || raw.isBlank() ? null : LocalDate.parse(raw);
    }

    private LocalDateTime nullableDateTime(String raw) {
        return raw == null || raw.isBlank() ? null : LocalDateTime.parse(raw);
    }

    private String nullableString(String raw) {
        return raw == null ? "" : raw;
    }
}