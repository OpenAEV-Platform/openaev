package io.openaev.migration;

import io.openaev.database.model.Variable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_67__Variables_Enum extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();
    // Alter column type for ENUM
    select.execute("ALTER TABLE variables ALTER COLUMN variable_type TYPE varchar(255)");
    // Migration datas
    ResultSet results = select.executeQuery("SELECT * FROM variables");
    PreparedStatement statement =
        connection.prepareStatement("UPDATE variables SET variable_type = ? WHERE variable_id = ?");
    while (results.next()) {
      String id = results.getString("variable_id");
      int type = results.getInt("variable_type");
      Variable.VariableType newType = Variable.VariableType.values()[type];
      statement.setString(1, String.valueOf(newType));
      statement.setString(2, id);
      statement.addBatch();
    }
    statement.executeBatch();
  }
}
