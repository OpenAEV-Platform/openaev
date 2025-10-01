package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_66__Attack_patterns extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();
    // Kill chain phases
    select.execute(
        """
          CREATE TABLE kill_chain_phases (
            phase_id varchar(255) not null constraint kill_chain_phases_pkey primary key,
            phase_created_at timestamp not null default now(),
            phase_updated_at timestamp not null default now(),
            phase_name varchar(255) not null,
            phase_kill_chain_name varchar(255) not null,
            phase_order bigint not null
          );
          CREATE INDEX idx_kill_chain_phases on kill_chain_phases (phase_id);
          CREATE UNIQUE INDEX kill_chain_phases_unique on kill_chain_phases (phase_name, phase_kill_chain_name);
     """);
    // Attack patterns
    select.execute(
        """
          CREATE TABLE attack_patterns (
            attack_pattern_id varchar(255) not null constraint attack_patterns_pkey primary key,
            attack_pattern_created_at timestamp not null default now(),
            attack_pattern_updated_at timestamp not null default now(),
            attack_pattern_name varchar(255) not null,
            attack_pattern_description text,
            attack_pattern_external_id varchar(255) not null,
            attack_pattern_platforms text[],
            attack_pattern_permissions_required text[],
            attack_pattern_parent varchar(255)
                constraint attack_pattern_parent_fk
                    references attack_patterns
                    on delete cascade
          );
          CREATE INDEX idx_attack_patterns on attack_patterns (attack_pattern_id);
          CREATE UNIQUE INDEX attack_patterns_unique on attack_patterns (attack_pattern_external_id);
     """);
    select.execute(
        """
        CREATE TABLE attack_patterns_kill_chain_phases (
            attack_pattern_id varchar(255) not null
                constraint attack_pattern_id_fk
                    references attack_patterns
                    on delete cascade,
            phase_id varchar(255) not null
                constraint phase_id_fk
                    references kill_chain_phases
                    on delete cascade,
            primary key (attack_pattern_id, phase_id)
        );
        CREATE INDEX idx_attack_patterns_kill_chain_phases_attack_pattern on attack_patterns_kill_chain_phases (attack_pattern_id);
        CREATE INDEX idx_attack_patterns_kill_chain_phases_kill_chain_phase on attack_patterns_kill_chain_phases (phase_id);
    """);
    // Cleanup a bit indexes
    select.execute(
        """
        CREATE INDEX idx_teams on teams (team_id);
        ALTER INDEX idx_medias RENAME TO idx_channels;
        ALTER INDEX idx_4e039727729413d0 RENAME TO idx_comchecks;
        ALTER INDEX idx_article_media_exercise RENAME TO idx_articles_channel_exercise;
        ALTER INDEX idx_1483a5e941221f7e RENAME TO idx_users_organization;
        ALTER INDEX idx_cfb417fca76ed395 RENAME TO idx_users_teams_user;
        ALTER INDEX idx_cfb417fccb0ca5a3 RENAME TO idx_users_teams_team;
    """);
  }
}
