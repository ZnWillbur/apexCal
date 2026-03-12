package com.apexcal.infrastructure.persistence;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

public final class DatabaseManager {
    private final Path databasePath;

    public DatabaseManager(Path dataDirectory) {
        this.databasePath = Objects.requireNonNull(dataDirectory, "dataDirectory").resolve("apexcal.db");
    }

    public void initialize() {
        try {
            Files.createDirectories(databasePath.getParent());
            try (Connection connection = openConnection()) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("PRAGMA foreign_keys = ON");
                    statement.execute("PRAGMA busy_timeout = 5000");
                }
                executeSchema(connection);
                ensureColumn(connection, "tasks", "source_tag", "TEXT NOT NULL DEFAULT 'USER'");
                ensureColumn(connection, "tasks", "metadata_json", "TEXT NOT NULL DEFAULT '{}'");
            }
        } catch (IOException | SQLException exception) {
            throw new IllegalStateException("无法初始化 SQLite 数据库", exception);
        }
    }

    public Connection openConnection() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath.toAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
        }
        return connection;
    }

    public Path databasePath() {
        return databasePath;
    }

    private void executeSchema(Connection connection) throws IOException, SQLException {
        try (InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("db/schema.sql")) {
            if (inputStream == null) {
                throw new IllegalStateException("缺少数据库 schema.sql 资源");
            }
            String script = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            for (String rawStatement : script.split(";")) {
                String statementText = rawStatement.trim();
                if (statementText.isEmpty()) {
                    continue;
                }
                try (Statement statement = connection.createStatement()) {
                    statement.execute(statementText);
                }
            }
        }
    }

    private void ensureColumn(Connection connection, String tableName, String columnName, String definition) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA table_info(" + tableName + ")")) {
            while (resultSet.next()) {
                if (columnName.equalsIgnoreCase(resultSet.getString("name"))) {
                    return;
                }
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + definition);
        }
    }
}