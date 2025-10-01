package io.openaev.migration;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class V3_16__Add_array_union_agg_method extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    try (Statement statement = connection.createStatement()) {
      ResultSet rs =
          statement.executeQuery(
              "SELECT EXISTS (SELECT 1 FROM pg_proc WHERE proname = 'array_union');");

      boolean functionExists = false;
      if (rs.next()) {
        functionExists = rs.getBoolean(1);
      }
      if (!functionExists) {
        statement.execute(
            "CREATE FUNCTION array_union(a ANYARRAY, b ANYARRAY)"
                + "    RETURNS ANYARRAY AS"
                + "    $$"
                + "SELECT array_agg(DISTINCT x)"
                + "FROM ("
                + "         SELECT unnest(a) x"
                + "         UNION ALL SELECT unnest(b)"
                + "     ) AS u"
                + "    $$ LANGUAGE SQL;"
                + "CREATE AGGREGATE array_union_agg(ANYARRAY) ("
                + "  SFUNC = array_union,"
                + "  STYPE = ANYARRAY,"
                + "  INITCOND = '{}'"
                + ");");
      }
    }
  }
}
