package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Creates the Spring Session JDBC tables so HTTP sessions are persisted in PostgreSQL and survive
 * platform restarts (no more forced logout on every upgrade/restart).
 *
 * <p>Schema mirrors the official Spring Session {@code schema-postgresql.sql} (session metadata +
 * serialized attributes). {@code spring.session.jdbc.initialize-schema} is set to {@code never}:
 * this migration is the single owner of the schema.
 *
 * <p>Idempotent ({@code IF NOT EXISTS} everywhere) and lock-light (creates brand-new tables, no
 * existing data touched).
 */
@Component
public class V6_20260718190000000__Create_spring_session_tables extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          CREATE TABLE IF NOT EXISTS spring_session (
            primary_id CHAR(36) NOT NULL,
            session_id CHAR(36) NOT NULL,
            creation_time BIGINT NOT NULL,
            last_access_time BIGINT NOT NULL,
            max_inactive_interval INT NOT NULL,
            expiry_time BIGINT NOT NULL,
            principal_name VARCHAR(100),
            CONSTRAINT spring_session_pk PRIMARY KEY (primary_id)
          );
          """);
      statement.execute(
          "CREATE UNIQUE INDEX IF NOT EXISTS spring_session_ix1 ON spring_session (session_id);");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS spring_session_ix2 ON spring_session (expiry_time);");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS spring_session_ix3 ON spring_session (principal_name);");
      statement.execute(
          """
          CREATE TABLE IF NOT EXISTS spring_session_attributes (
            session_primary_id CHAR(36) NOT NULL,
            attribute_name VARCHAR(200) NOT NULL,
            attribute_bytes BYTEA NOT NULL,
            CONSTRAINT spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name),
            CONSTRAINT spring_session_attributes_fk FOREIGN KEY (session_primary_id)
              REFERENCES spring_session (primary_id) ON DELETE CASCADE
          );
          """);
    }
  }
}
