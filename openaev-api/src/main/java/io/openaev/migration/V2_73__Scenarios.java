package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_73__Scenarios extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();
    // Create scenario table
    select.execute(
        """
        CREATE TABLE IF NOT EXISTS scenarios (
            scenario_id varchar(255) not null constraint scenarios_pkey primary key,
            scenario_name varchar(255) not null,
            scenario_description text,
            scenario_subtitle text,
            scenario_message_header varchar(255),
            scenario_message_footer varchar(255),
            scenario_mail_from text not null,
            scenario_created_at timestamp not null default now(),
            scenario_updated_at timestamp not null default now()
        );
        CREATE INDEX IF NOT EXISTS idx_assets on assets (asset_id);
        """);
    // Add association table between scenario and team and player
    select.execute(
        """
            CREATE TABLE scenarios_teams_users (
                scenario_id varchar(255) not null constraint scenario_id_fk references scenarios on delete cascade,
                team_id varchar(255) not null constraint team_id_fk references teams on delete cascade,
                user_id varchar(255) not null constraint user_id_fk references users on delete cascade,
                primary key (scenario_id, team_id, user_id)
            );
            CREATE INDEX idx_scenarios_teams_users_scenario on scenarios_teams_users (scenario_id);
            CREATE INDEX idx_scenarios_teams_users_team on scenarios_teams_users (team_id);
            CREATE INDEX idx_scenarios_teams_users_user on scenarios_teams_users (user_id);
        """);
    // Add scenario to grant
    select.execute(
        """
        ALTER TABLE grants ADD COLUMN grant_scenario varchar(255) constraint scenario_fk references scenarios on delete cascade ;
        """);
    // Add scenario to inject
    select.execute(
        """
        ALTER TABLE injects ADD COLUMN inject_scenario varchar(255) constraint scenario_fk references scenarios on delete cascade ;
        """);
    // Add association table between scenario and team
    select.execute(
        """
        CREATE TABLE scenarios_teams (
          scenario_id varchar(255) not null constraint scenario_id_fk references scenarios,
          team_id varchar(255) not null constraint team_id_fk references teams,
          constraint scenarios_teams_pkey primary key (scenario_id, team_id)
        );
        CREATE INDEX idx_scenarios_teams_scenario on scenarios_teams (scenario_id);
        CREATE INDEX idx_scenarios_teams_team on scenarios_teams (team_id);
        """);
    // Add scenario to objective
    select.execute(
        """
        ALTER TABLE objectives ADD COLUMN objective_scenario varchar(255) constraint scenario_fk references scenarios on delete cascade ;
        """);
    // Add association table between scenario and tag
    select.execute(
        """
        CREATE TABLE scenarios_tags (
          scenario_id varchar(255) not null constraint scenario_id_fk references scenarios,
          tag_id varchar(255) not null constraint tag_id_fk references tags,
          constraint scenarios_tags_pkey primary key (scenario_id, tag_id)
        );
        CREATE INDEX idx_scenarios_tags_scenario on scenarios_tags (scenario_id);
        CREATE INDEX idx_scenarios_tags_tag on scenarios_tags (tag_id);
        """);
    // Add association table between scenario and tag
    select.execute(
        """
        CREATE TABLE scenarios_documents (
          scenario_id varchar(255) not null constraint scenario_id_fk references scenarios,
          document_id varchar(255) not null constraint document_id_fk references documents,
          constraint scenarios_documents_pkey primary key (scenario_id, document_id)
        );
        CREATE INDEX idx_scenarios_documents_scenario on scenarios_documents (scenario_id);
        CREATE INDEX idx_scenarios_documents_document on scenarios_documents (document_id);
        """);
    // Add scenario to article
    select.execute(
        """
        ALTER TABLE articles ALTER COLUMN article_exercise DROP NOT NULL ;
        ALTER TABLE articles ADD COLUMN article_scenario varchar(255) constraint scenario_fk references scenarios on delete cascade ;
        """);
    // Add scenario to lessons categories
    select.execute(
        """
        ALTER TABLE lessons_categories ALTER COLUMN lessons_category_exercise DROP NOT NULL ;
        ALTER TABLE lessons_categories ADD COLUMN lessons_category_scenario varchar(255) constraint scenario_fk references scenarios on delete cascade ;
        """);
    // Add scenario to group
    select.execute(
        """
        create table groups_scenarios_default_grants (
            group_id varchar(255) not null constraint group_id_fk references groups,
            scenarios_default_grants varchar(255)
        );
        """);
    // Add scenario to variables
    select.execute(
        """
        ALTER TABLE variables ADD COLUMN variable_scenario varchar(255) default NULL::character varying constraint scenario_id_fk references scenarios on delete cascade;
        CREATE INDEX IF NOT EXISTS idx_variable_scenario on variables (variable_scenario);
        """);
  }
}
