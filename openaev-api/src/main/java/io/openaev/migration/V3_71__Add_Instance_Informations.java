package io.openaev.migration;

import static io.openaev.database.model.SettingKeys.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_71__Add_Instance_Informations extends BaseJavaMigration {
  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement statement = connection.createStatement();

    // Get creation instance date
    ResultSet firstMigration =
        statement.executeQuery(
            "SELECT installed_on FROM migrations WHERE installed_rank=1 LIMIT 1");

    if (firstMigration.next()) { // Ensure there's a result
      String insertSql =
          "INSERT INTO parameters (parameter_id, parameter_key, parameter_value) "
              + "VALUES (?, ?, ?), (?, ?, ?)";
      PreparedStatement preparedStatement = connection.prepareStatement(insertSql);
      preparedStatement.setString(1, String.valueOf(UUID.randomUUID()));
      preparedStatement.setString(2, PLATFORM_INSTANCE.key()); // Replace with the actual key
      preparedStatement.setString(3, String.valueOf(UUID.randomUUID()));

      preparedStatement.setString(4, String.valueOf(UUID.randomUUID()));
      preparedStatement.setString(
          5, PLATFORM_INSTANCE_CREATION.key()); // Replace with the actual key
      preparedStatement.setString(6, String.valueOf(firstMigration.getTimestamp("installed_on")));

      preparedStatement.executeUpdate();
    }
  }
}
