package io.openaev.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.InjectDependencyConditions;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_46__Add_table_inject_dependencies extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    Statement select = context.getConnection().createStatement();
    select.execute(
        """
                        CREATE TABLE injects_dependencies (
                            inject_parent_id VARCHAR(255) NOT NULL REFERENCES injects(inject_id) ON DELETE CASCADE,
                            inject_children_id VARCHAR(255) NOT NULL REFERENCES injects(inject_id) ON DELETE CASCADE,
                            dependency_condition JSONB,
                            dependency_created_at TIMESTAMP DEFAULT now(),
                            dependency_updated_at TIMESTAMP DEFAULT now(),
                            PRIMARY KEY(inject_parent_id, inject_children_id)
                          );
                          CREATE INDEX idx_injects_dependencies ON injects_dependencies(inject_children_id);
                """);

    // Migration datas
    ResultSet results =
        select.executeQuery("SELECT * FROM injects WHERE inject_depends_from_another IS NOT NULL");
    PreparedStatement statement =
        context
            .getConnection()
            .prepareStatement(
                """
                INSERT INTO injects_dependencies(inject_parent_id, inject_children_id, dependency_condition)
                VALUES (?, ?, to_json(?::json))
                """);
    while (results.next()) {
      String injectId = results.getString("inject_id");
      String parentId = results.getString("inject_depends_from_another");
      InjectDependencyConditions.InjectDependencyCondition injectDependencyCondition =
          new InjectDependencyConditions.InjectDependencyCondition();
      injectDependencyCondition.setMode(InjectDependencyConditions.DependencyMode.and);
      InjectDependencyConditions.Condition condition = new InjectDependencyConditions.Condition();
      condition.setKey("Execution");
      condition.setOperator(InjectDependencyConditions.DependencyOperator.eq);
      condition.setValue(true);
      injectDependencyCondition.setConditions(List.of(condition));
      statement.setString(1, parentId);
      statement.setString(2, injectId);
      statement.setString(3, mapper.writeValueAsString(injectDependencyCondition));
      statement.addBatch();
    }
    statement.executeBatch();
  }
}
