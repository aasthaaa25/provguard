package com.provguard.desktop;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class SqliteCaseStore implements CaseStore {
    private static final String INSERT = """
            INSERT INTO cases(id, opened_at, title, source, status, notes) VALUES (?, ?, ?, ?, ?, ?)
            """;
    private static final String SELECT = "SELECT id, opened_at, title, source, status, notes FROM cases";
    private final String url;

    SqliteCaseStore(Path database) throws ReviewException {
        url = "jdbc:sqlite:" + database.toAbsolutePath();
        try (var connection = connect(); var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS cases (
                        id TEXT PRIMARY KEY NOT NULL,
                        opened_at TEXT NOT NULL,
                        title TEXT NOT NULL,
                        source TEXT NOT NULL,
                        status TEXT NOT NULL CHECK (status IN ('OPEN', 'IN_REVIEW', 'CLOSED')),
                        notes TEXT NOT NULL
                    )
                    """);
        } catch (SQLException exception) {
            throw new ReviewException("Could not open the case database.", exception);
        }
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(url);
    }

    @Override
    public List<ReviewCase> list() throws ReviewException {
        List<ReviewCase> items = new ArrayList<>();
        try (var connection = connect(); var query = connection.prepareStatement(SELECT);
             var result = query.executeQuery()) {
            while (result.next()) {
                items.add(read(result));
            }
            return items;
        } catch (SQLException exception) {
            throw new ReviewException("Could not load cases.", exception);
        }
    }

    @Override
    public Optional<ReviewCase> find(UUID id) throws ReviewException {
        try (var connection = connect(); var query = connection.prepareStatement(SELECT + " WHERE id = ?")) {
            query.setString(1, id.toString());
            try (var result = query.executeQuery()) {
                return result.next() ? Optional.of(read(result)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new ReviewException("Could not load the selected case.", exception);
        }
    }

    @Override
    public void insert(ReviewCase item) throws ReviewException {
        try (var connection = connect(); var statement = connection.prepareStatement(INSERT)) {
            bind(statement, item);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new ReviewException("Could not create the case.", exception);
        }
    }

    @Override
    public void update(ReviewCase item) throws ReviewException {
        try (var connection = connect(); var statement = connection.prepareStatement(
                "UPDATE cases SET title = ?, status = ?, notes = ? WHERE id = ?")) {
            statement.setString(1, item.title());
            statement.setString(2, item.status().name());
            statement.setString(3, item.notes());
            statement.setString(4, item.id().toString());
            requireOne(statement);
        } catch (SQLException exception) {
            throw new ReviewException("Could not update the case.", exception);
        }
    }

    @Override
    public void delete(UUID id) throws ReviewException {
        try (var connection = connect(); var statement = connection.prepareStatement("DELETE FROM cases WHERE id = ?")) {
            statement.setString(1, id.toString());
            requireOne(statement);
        } catch (SQLException exception) {
            throw new ReviewException("Could not delete the case.", exception);
        }
    }

    @Override
    public int importCases(List<ReviewCase> items) throws ReviewException {
        try (var connection = connect()) {
            connection.setAutoCommit(false);
            try {
                int inserted = 0;
                try (var statement = connection.prepareStatement(INSERT + " ON CONFLICT(id) DO NOTHING")) {
                    for (ReviewCase item : items) {
                        bind(statement, item);
                        inserted += statement.executeUpdate();
                    }
                }
                connection.commit();
                return inserted;
            } catch (SQLException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackError) {
                    exception.addSuppressed(rollbackError);
                }
                throw exception;
            }
        } catch (SQLException exception) {
            throw new ReviewException("The case import failed. No partial batch was committed.", exception);
        }
    }

    private static ReviewCase read(ResultSet row) throws SQLException {
        return new ReviewCase(UUID.fromString(row.getString("id")), LocalDateTime.parse(row.getString("opened_at")),
                row.getString("title"), row.getString("source"), ReviewCase.Status.valueOf(row.getString("status")),
                row.getString("notes"));
    }

    private static void bind(PreparedStatement statement, ReviewCase item) throws SQLException {
        statement.setString(1, item.id().toString());
        statement.setString(2, item.openedAt().toString());
        statement.setString(3, item.title());
        statement.setString(4, item.source());
        statement.setString(5, item.status().name());
        statement.setString(6, item.notes());
    }

    private static void requireOne(PreparedStatement statement) throws SQLException {
        if (statement.executeUpdate() != 1) {
            throw new SQLException("Case no longer exists.");
        }
    }
}
