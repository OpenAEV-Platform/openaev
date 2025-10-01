package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_70__Enhance_attack_patterns extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();
    // Cleanup a bit indexes
    select.execute(
        """
        ALTER TABLE users_teams RENAME CONSTRAINT fk_cfb417fca76ed395 TO fk_user_id;
        ALTER TABLE users_teams RENAME CONSTRAINT fk_cfb417fccb0ca5a3 TO fk_team_id;
        ALTER TABLE injects_tags RENAME CONSTRAINT tag_id_fk TO fk_tag_id;
        ALTER TABLE injects_tags RENAME CONSTRAINT inject_id_fk TO fk_inject_id;
        ALTER TABLE groups_organizations RENAME CONSTRAINT group_id_fk TO fk_group_id;
        ALTER TABLE groups_organizations RENAME CONSTRAINT organization_id_fk TO fk_organization_id;
        ALTER TABLE dryruns_users RENAME CONSTRAINT dryrun_id_fk TO fk_dryrun_id;
        ALTER TABLE dryruns_users RENAME CONSTRAINT user_id_fk TO fk_user_id;
        ALTER TABLE attack_patterns_kill_chain_phases RENAME CONSTRAINT attack_pattern_id_fk TO fk_attack_pattern_id;
        ALTER TABLE attack_patterns_kill_chain_phases RENAME CONSTRAINT phase_id_fk TO fk_phase_id;
        ALTER TABLE articles_documents RENAME CONSTRAINT article_id_fk TO fk_article_id;
        ALTER TABLE articles_documents RENAME CONSTRAINT document_id_fk TO fk_document_id;
        ALTER TABLE exercises_teams_users RENAME CONSTRAINT exercise_id_fk TO fk_exercise_id;
        ALTER TABLE exercises_teams_users RENAME CONSTRAINT team_id_fk TO fk_team_id;
        ALTER TABLE exercises_teams_users RENAME CONSTRAINT user_id_fk TO fk_user_id;
        ALTER TABLE exercises_teams RENAME CONSTRAINT exercise_id_fk TO fk_exercise_id;
        ALTER TABLE exercises_teams RENAME CONSTRAINT team_id_fk TO fk_team_id;
        ALTER INDEX attack_patterns_unique RENAME TO idx_attack_patterns_external_id;
        ALTER INDEX attack_patterns_pkey RENAME TO pkey_attack_patterns;
        ALTER INDEX articles_pkey RENAME TO pkey_articles;
        ALTER INDEX idx_articles RENAME TO idx_articles_id;
        ALTER INDEX articles_documents_pkey RENAME TO pkey_articles_documents;
        ALTER INDEX attack_patterns_kill_chain_phases_pkey RENAME TO pkey_attack_patterns_kill_chain_phases;
    """);
    // Add column for endpoint type
    select.execute(
        """
        ALTER TABLE kill_chain_phases ADD COLUMN phase_description text;
        ALTER TABLE kill_chain_phases ADD COLUMN phase_shortname varchar(255) not null;
        ALTER TABLE kill_chain_phases ADD COLUMN phase_external_id varchar(255) not null;
        ALTER TABLE kill_chain_phases ADD COLUMN phase_stix_id varchar(255);
        ALTER TABLE attack_patterns ADD COLUMN attack_pattern_stix_id varchar(255);
        CREATE UNIQUE INDEX idx_attack_patterns_stix_id on attack_patterns (attack_pattern_stix_id);
        CREATE UNIQUE INDEX idx_kill_chain_phases_stix_id on kill_chain_phases (phase_stix_id);
    """);
  }
}
