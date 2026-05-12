package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V5_08__Create_url_access_token extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.addBatch(
          """
          CREATE TABLE IF NOT EXISTS url_access_token (
              id              VARCHAR(255) PRIMARY KEY DEFAULT gen_random_uuid(),
              token_hash      VARCHAR(64)  NOT NULL,
              url             VARCHAR      NOT NULL,
              user_id         VARCHAR(255) NOT NULL,
              exercise_id     VARCHAR(255) NOT NULL,
              expires_at      TIMESTAMP    NULL,
              revoked_at      TIMESTAMP    NULL,
              last_used_at    TIMESTAMP    NULL,
              created_at      TIMESTAMP    NOT NULL DEFAULT now(),
              creator_user_id VARCHAR(255) NULL,
              CONSTRAINT fk_url_access_token_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
              CONSTRAINT fk_url_access_token_exercise FOREIGN KEY (exercise_id) REFERENCES exercises(exercise_id) ON DELETE CASCADE,
              CONSTRAINT fk_url_access_token_creator FOREIGN KEY (creator_user_id) REFERENCES users(user_id) ON DELETE SET NULL
          )
      """);

      statement.addBatch(
          "CREATE UNIQUE INDEX IF NOT EXISTS idx_url_access_token_token_hash ON url_access_token(token_hash)");
      statement.addBatch(
          "CREATE INDEX IF NOT EXISTS idx_url_access_token_expires_at ON url_access_token(expires_at)");
      statement.addBatch(
          "CREATE INDEX IF NOT EXISTS idx_url_access_token_exercise_id ON url_access_token(exercise_id)");
      statement.addBatch(
          "CREATE INDEX IF NOT EXISTS idx_url_access_token_user_id ON url_access_token(user_id)");

      statement.executeBatch();
    }
  }
}

// ROLLBACK
// DROP INDEX IF EXISTS idx_url_access_token_user_id;
// DROP INDEX IF EXISTS idx_url_access_token_exercise_id;
// DROP INDEX IF EXISTS idx_url_access_token_expires_at;
// DROP INDEX IF EXISTS idx_url_access_token_token_hash;
// DROP TABLE IF EXISTS url_access_token;
