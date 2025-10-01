package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_76__Add_challenge_attempt extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement statement = connection.createStatement();
    statement.execute(
        """
        CREATE TABLE challenge_attempts (
            challenge_id VARCHAR(255) NOT NULL,
            inject_status_id VARCHAR(255) NOT NULL,
            user_id VARCHAR(255) NOT NULL,
            challenge_attempt INTEGER NOT NULL DEFAULT 0,
            attempt_created_at TIMESTAMP(0) WITH TIME ZONE NOT NULL DEFAULT now(),
            attempt_updated_at TIMESTAMP(0) WITH TIME ZONE NOT NULL DEFAULT now(),
            CONSTRAINT pk_challenge_attempts PRIMARY KEY (challenge_id, inject_status_id, user_id),
            CONSTRAINT fk_challenge_attempt_challenge FOREIGN KEY (challenge_id) REFERENCES challenges (challenge_id) ON DELETE CASCADE,
            CONSTRAINT fk_challenge_attempt_inject_status FOREIGN KEY (inject_status_id) REFERENCES injects_statuses (status_id) ON DELETE CASCADE,
            CONSTRAINT fk_challenge_attempt_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE
        );
        CREATE INDEX idx_challenge_attempt_challenge_id ON challenge_attempts(challenge_id);
        CREATE INDEX idx_challenge_attempt_inject_status_id ON challenge_attempts(inject_status_id);
        CREATE INDEX idx_challenge_attempt_user_id ON challenge_attempts(user_id);
        """);
  }
}
