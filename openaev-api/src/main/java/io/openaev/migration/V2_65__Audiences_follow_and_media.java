package io.openaev.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_65__Audiences_follow_and_media extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();
    // Medias
    select.execute(
        """
        ALTER TABLE teams RENAME CONSTRAINT team_pkey TO teams_pkey;
        ALTER TABLE medias RENAME CONSTRAINT medias_pkey TO channels_pkey;
        ALTER TABLE medias RENAME COLUMN media_id TO channel_id;
        ALTER TABLE medias RENAME COLUMN media_name TO channel_name;
        ALTER TABLE medias RENAME COLUMN media_type TO channel_type;
        ALTER TABLE medias RENAME COLUMN media_description TO channel_description;
        ALTER TABLE medias RENAME COLUMN media_logo_dark TO channel_logo_dark;
        ALTER TABLE medias RENAME COLUMN media_logo_light TO channel_logo_light;
        ALTER TABLE medias RENAME COLUMN media_primary_color_dark TO channel_primary_color_dark;
        ALTER TABLE medias RENAME COLUMN media_primary_color_light TO channel_primary_color_light;
        ALTER TABLE medias RENAME COLUMN media_secondary_color_dark TO channel_secondary_color_dark;
        ALTER TABLE medias RENAME COLUMN media_secondary_color_light TO channel_secondary_color_light;
        ALTER TABLE medias RENAME COLUMN media_mode TO channel_mode;
        ALTER TABLE medias RENAME COLUMN media_created_at TO channel_created_at;
        ALTER TABLE medias RENAME COLUMN media_updated_at TO channel_updated_at;
        ALTER TABLE medias RENAME TO channels;
     """);
    select.execute(
        """
      ALTER TABLE articles RENAME CONSTRAINT fk_article_media TO fk_article_channel;
      ALTER TABLE articles RENAME COLUMN article_media TO article_channel;
    """);
    // Add Variable table
    select.execute(
        """
        ALTER TABLE teams ADD team_contextual bool default false;
    """);
    // Migration the data
    ObjectMapper mapper = new ObjectMapper();
    ResultSet results =
        select.executeQuery(
            "SELECT * FROM injects_teams it LEFT JOIN injects as inject ON it.inject_id = inject.inject_id");
    PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO exercises_teams (exercise_id, team_id) SELECT ?, ? WHERE NOT EXISTS (SELECT exercise_id FROM exercises_teams WHERE exercise_id = ? and team_id = ?)");
    while (results.next()) {
      String exerciseId = results.getString("inject_exercise");
      String teamId = results.getString("team_id");
      statement.setString(1, exerciseId);
      statement.setString(2, teamId);
      statement.setString(3, exerciseId);
      statement.setString(4, teamId);
      statement.addBatch();
    }
    statement.executeBatch();
  }
}
