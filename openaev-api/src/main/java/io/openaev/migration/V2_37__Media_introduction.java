package io.openaev.migration;

import java.sql.*;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_37__Media_introduction extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();
    // Medias
    select.execute(
        """
                CREATE TABLE medias
                (
                    media_id varchar(255) not null constraint medias_pkey primary key,
                    media_created_at timestamp not null default now(),
                    media_updated_at timestamp not null default now(),
                    media_name text,
                    media_color varchar(255),
                    media_published bool default true
                );
                CREATE INDEX idx_medias on medias (media_id);
                """);
    // Create articles
    select.execute(
        """
                CREATE TABLE articles (
                    article_id varchar(255) not null constraint articles_pkey primary key,
                    article_created_at timestamp not null default now(),
                    article_updated_at timestamp not null default now(),
                    article_name text,
                    article_header text,
                    article_content text,
                    article_footer text,
                    article_published bool default false,
                    article_media varchar(255) not null
                        constraint fk_article_media references medias on delete cascade,
                    article_exercise varchar(255) not null
                        constraint fk_article_exercise references exercises on delete cascade
                );
                CREATE INDEX idx_articles on articles (article_id);
                CREATE INDEX idx_article_media_exercise on articles (article_media, article_exercise);
                """);
    // Challenges
    select.execute(
        """
                CREATE TABLE challenges (
                    challenge_id varchar(255) not null constraint challenges_pkey primary key,
                    challenge_created_at timestamp not null default now(),
                    challenge_updated_at timestamp not null default now(),
                    challenge_name text,
                    challenge_description text,
                    challenge_flag text
                );
                CREATE INDEX idx_challenges on challenges (challenge_id);
                """);
    // Expectations
    select.execute(
        """
                CREATE TABLE injects_expectations (
                    inject_expectation_id varchar(255) not null constraint injects_expectations_pkey primary key,
                    inject_expectation_type varchar(255) not null,
                    inject_id varchar(255) not null
                        constraint fk_expectations_inject references injects on delete cascade,
                    article_id varchar(255)
                        constraint fk_article references articles on delete cascade,
                    challenge_id varchar(255)
                        constraint fk_challenge references challenges on delete cascade
                );
                CREATE INDEX idx_injects_expectations on injects_expectations (inject_expectation_id);
                """);
    // Inject user expectation
    select.execute(
        """
                CREATE TABLE injects_expectations_executions (
                    expectation_execution_id varchar(255) not null constraint expectations_pkey primary key,
                    expectation_execution_created_at timestamp not null default now(),
                    expectation_execution_updated_at timestamp not null default now(),
                    expectation_execution_result text,
                    inject_expectation_id varchar(255) not null
                        constraint fk_inject_expectation references injects_expectations on delete cascade,
                    user_id varchar(255) not null
                        constraint fk_expectations_user references users on delete cascade
                );
                CREATE INDEX idx_injects_expectations_executions on injects_expectations_executions (expectation_execution_id);
                """);
  }
}
