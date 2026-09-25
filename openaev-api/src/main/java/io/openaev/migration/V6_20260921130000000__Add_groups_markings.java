package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * {@code groups_markings} — the clearance <b>grant</b>: which markings the members of a group are
 * allowed to see (Task 2, step 2.1 of the marking design). Answers "what can this group see?", not
 * "who may see this group", which is why it stays a join table and never becomes a marked table
 * itself.
 *
 * <p>This table was originally created alongside {@code marking_definitions} in an earlier revision
 * of this branch. It was lost when that combined migration was superseded, during the main merge,
 * by the dedicated {@code marking_definitions}-only migration from Task 1 (#7651) — this migration
 * restores the join table on its own, against the current {@code marking_definitions} shape ({@code
 * marking_definition_id}, not the old {@code marking_id}).
 */
@Component
public class V6_20260921130000000__Add_groups_markings extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          CREATE TABLE IF NOT EXISTS groups_markings (
            group_id   VARCHAR(255) NOT NULL
              CONSTRAINT fk_groups_markings_group_id
              REFERENCES groups (group_id) ON DELETE CASCADE,
            marking_id VARCHAR(255) NOT NULL
              CONSTRAINT fk_groups_markings_marking_id
              REFERENCES marking_definitions (marking_definition_id) ON DELETE CASCADE,
            CONSTRAINT groups_markings_pkey PRIMARY KEY (group_id, marking_id)
          );
          """);
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_groups_markings_marking_id ON groups_markings (marking_id);");
    }
  }
}
