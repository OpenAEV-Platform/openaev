package io.openaev.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Component
public class V6_20260707142145235__Dynamic_base_url_on_injectors_commands extends BaseJavaMigration {

  private static final String PLACEHOLDER = "#{baseUrl}";
  private static final List<String> PROPERTY_KEYS = List.of("openaev.base-url", "openbas.base-url");
  private static final List<String> ENV_KEYS = List.of("OPENAEV_BASE_URL", "openaev.base-url");

  @Override
  public void migrate(Context context) throws Exception {
    Optional<String> configuredBaseUrl = resolveBaseUrl();
    if (configuredBaseUrl.isEmpty()) {
      return;
    }

    String baseUrl = normalizeUrl(configuredBaseUrl.get());
    if (baseUrl == null || baseUrl.isBlank() || PLACEHOLDER.equals(baseUrl)) {
      return;
    }

    try (Connection connection = context.getConnection()) {
      if (!tableExists(connection, "injectors")
          || !columnExists(connection, "injectors", "injector_executor_commands")
          || !columnExists(connection, "injectors", "injector_executor_clear_commands")) {
        return;
      }

      replaceBaseUrlInCommands(connection, baseUrl);
    }
  }

  private void replaceBaseUrlInCommands(Connection connection, String baseUrl) throws SQLException {
    String updateSql =
        """
        UPDATE injectors i
        SET
          injector_executor_commands = CASE
            WHEN i.injector_executor_commands IS NULL THEN NULL
            ELSE (
              SELECT hstore(array_agg(e.key), array_agg(
                CASE WHEN e.value IS NULL THEN NULL ELSE replace(e.value, ?, ?) END
              ))
              FROM each(i.injector_executor_commands) e
            )
          END,
          injector_executor_clear_commands = CASE
            WHEN i.injector_executor_clear_commands IS NULL THEN NULL
            ELSE (
              SELECT hstore(array_agg(e.key), array_agg(
                CASE WHEN e.value IS NULL THEN NULL ELSE replace(e.value, ?, ?) END
              ))
              FROM each(i.injector_executor_clear_commands) e
            )
          END
        WHERE
          EXISTS (
            SELECT 1 FROM each(i.injector_executor_commands) e
            WHERE e.value IS NOT NULL AND position(? in e.value) > 0
          )
          OR EXISTS (
            SELECT 1 FROM each(i.injector_executor_clear_commands) e
            WHERE e.value IS NOT NULL AND position(? in e.value) > 0
          )
        """;

    try (PreparedStatement statement = connection.prepareStatement(updateSql)) {
      statement.setString(1, baseUrl);
      statement.setString(2, PLACEHOLDER);
      statement.setString(3, baseUrl);
      statement.setString(4, PLACEHOLDER);
      statement.setString(5, baseUrl);
      statement.setString(6, baseUrl);
      statement.executeUpdate();
    }
  }

  private Optional<String> resolveBaseUrl() {
    for (String key : PROPERTY_KEYS) {
      String value = System.getProperty(key);
      if (value != null && !value.isBlank()) {
        return Optional.of(value.trim());
      }
    }
    for (String key : ENV_KEYS) {
      String value = System.getenv(key);
      if (value != null && !value.isBlank()) {
        return Optional.of(value.trim());
      }
    }
    return Optional.empty();
  }

  private String normalizeUrl(String url) {
    String trimmed = url.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
  }

  private boolean tableExists(Connection connection, String tableName) throws SQLException {
    String sql =
        """
        SELECT EXISTS (
          SELECT 1
          FROM information_schema.tables
          WHERE table_schema = current_schema() AND table_name = ?
        )
        """;
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, tableName);
      try (ResultSet resultSet = statement.executeQuery()) {
        resultSet.next();
        return resultSet.getBoolean(1);
      }
    }
  }

  private boolean columnExists(Connection connection, String tableName, String columnName)
      throws SQLException {
    String sql =
        """
        SELECT EXISTS (
          SELECT 1
          FROM information_schema.columns
          WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?
        )
        """;
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, tableName);
      statement.setString(2, columnName);
      try (ResultSet resultSet = statement.executeQuery()) {
        resultSet.next();
        return resultSet.getBoolean(1);
      }
    }
  }
}
