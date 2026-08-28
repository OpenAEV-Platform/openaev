package io.openaev.migration;

public class ClassContentsGenerator {
  private final String migrationContentsTemplate =
      """
            package io.openaev.migration;

            import java.sql.Statement;
            import org.flywaydb.core.api.migration.BaseJavaMigration;
            import org.flywaydb.core.api.migration.Context;
            import org.springframework.stereotype.Component;

            @Component
            public class %s extends BaseJavaMigration {

              @Override
              public void migrate(Context context) throws Exception {
                try (Statement statement = context.getConnection().createStatement()) {
                  statement.execute("SELECT 1;");
                }
              }
            }
            """;

  public String generate(String className) {
    return migrationContentsTemplate.formatted(className);
  }
}
