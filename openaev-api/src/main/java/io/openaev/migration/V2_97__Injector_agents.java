package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_97__Injector_agents extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Statement select = context.getConnection().createStatement();
    select.execute("ALTER TABLE injectors ADD injector_simulation_agent bool default false;");
    select.execute("ALTER TABLE injectors ADD injector_simulation_agent_platforms text[];");
    select.execute("ALTER TABLE injectors ADD injector_simulation_agent_doc text;");
    select.execute("ALTER TABLE injectors ADD injector_category varchar(255);");
  }
}
