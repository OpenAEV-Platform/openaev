package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_25__Feedback_start extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Statement select = context.getConnection().createStatement();
    // Drop unused table outcomes
    select.execute("DROP TABLE IF EXISTS outcomes;");
    // Create evaluation table
    select.execute(
        """
                            create table evaluations
                            (
                                evaluation_id varchar(255) not null constraint evaluations_pkey primary key,
                                evaluation_score bigint,
                                evaluation_objective varchar(255) not null
                                    constraint fk_evaluation_objective references objectives on delete cascade,
                                evaluation_user varchar(255) not null
                                    constraint fk_evaluation_user references users on delete cascade,
                                evaluation_created_at timestamp not null default now(),
                                evaluation_updated_at timestamp not null default now()
                            );
                            create index idx_evaluations on evaluations (evaluation_id);
                """);
    // Create poll table
    select.execute(
        """
                            create table polls
                            (
                                poll_id varchar(255) not null constraint polls_pkey primary key,
                                poll_question text,
                                poll_exercise varchar(255) not null
                                    constraint fk_poll_exercise references exercises on delete cascade,
                                poll_created_at timestamp not null default now(),
                                poll_updated_at timestamp not null default now()
                            );
                            create index idx_polls on polls (poll_id);
                """);
    //noinspection SqlResolve
    select.execute(
        """
                            create table answers
                            (
                                answer_id varchar(255) not null constraint answers_pkey primary key,
                                answer_content text,
                                answer_evaluation bigint,
                                answer_poll varchar(255) not null
                                    constraint fk_answer_poll references polls on delete cascade,
                                answer_created_at timestamp not null default now(),
                                answer_updated_at timestamp not null default now()
                            );
                            create index idx_answers on answers (answer_id);
                """);
    // Replace log_date by log_created_at / log_updated_at for consistency
    select.execute("ALTER TABLE logs ADD log_created_at timestamp not null default now();");
    select.execute("ALTER TABLE logs ADD log_updated_at timestamp not null default now();");
    select.execute("ALTER TABLE logs DROP column log_date;");
    //  Implement "tags" on logs (log_tags table)
    select.execute(
        """
                CREATE TABLE logs_tags (
                    log_id varchar(255) not null constraint log_id_fk references logs on delete cascade,
                    tag_id varchar(255) not null constraint tag_id_fk references tags on delete cascade,
                    constraint logs_tags_pkey primary key (log_id, tag_id)
                );
                CREATE INDEX idx_logs_tags_log on logs_tags (log_id);
                CREATE INDEX idx_logs_tags_tag on logs_tags (tag_id);
                """);
  }
}
