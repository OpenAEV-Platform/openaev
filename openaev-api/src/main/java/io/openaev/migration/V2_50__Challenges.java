package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_50__Challenges extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Statement select = context.getConnection().createStatement();
    // Add association table between challenge and tag
    select.execute(
        """
                CREATE TABLE challenges_tags (
                    challenge_id varchar(255) not null constraint challenge_id_fk references challenges,
                    tag_id varchar(255) not null constraint tag_id_fk references tags,
                    constraint challenges_tags_pkey primary key (challenge_id, tag_id)
                );
                CREATE INDEX idx_challenge_tags_challenge on challenges_tags (challenge_id);
                CREATE INDEX idx_challenge_tags_tag on challenges_tags (tag_id);
                """);
    select.execute("ALTER TABLE challenges DROP column challenge_description;");
    select.execute("ALTER TABLE challenges ADD challenge_category varchar(255);");
    select.execute("ALTER TABLE challenges ADD challenge_content text;");
    select.execute("ALTER TABLE challenges ADD challenge_score int;");
    select.execute("ALTER TABLE challenges ADD challenge_max_attempts int;");
  }
}
