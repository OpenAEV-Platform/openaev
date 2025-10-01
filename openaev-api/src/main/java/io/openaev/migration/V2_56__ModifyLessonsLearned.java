package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_56__ModifyLessonsLearned extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();
    select.execute("DROP TABLE IF EXISTS lessons_questions_audiences;");
    select.execute(
        """
                CREATE TABLE lessons_categories_audiences (
                    audience_id varchar(255) not null constraint audience_id_fk references audiences on delete cascade,
                    lessons_category_id varchar(255) not null constraint lessons_category_id_fk references lessons_categories on delete cascade,
                    constraint lessons_categories_audiences_pkey primary key (audience_id, lessons_category_id)
                );
                CREATE INDEX idx_lessons_categories_audiences_category on lessons_categories_audiences (lessons_category_id);
                CREATE INDEX idx_lessons_categories_audiences_audience on lessons_categories_audiences (audience_id);
        """);
    select.execute("ALTER TABLE exercises ADD exercise_lessons_anonymized bool default false;");
  }
}
