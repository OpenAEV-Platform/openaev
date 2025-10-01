package io.openaev.migration;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class V1__Init extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement schemaStatement = connection.createStatement();
    // Table not exists, must initialize the schema
    ClassPathResource classPathResource = new ClassPathResource("application.sql");
    InputStream inputStream = classPathResource.getInputStream();
    String schemaQuery = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    schemaStatement.execute(schemaQuery);
  }
}
