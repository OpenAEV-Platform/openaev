package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_54__LessonsLearned extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();
    select.execute("DROP TABLE IF EXISTS answers;");
    select.execute("DROP TABLE IF EXISTS polls;");
    select.execute(
        """
                CREATE TABLE lessons_templates (
                    lessons_template_id varchar(255) not null constraint lessons_templates_pkey primary key,
                    lessons_template_created_at timestamp not null default now(),
                    lessons_template_updated_at timestamp not null default now(),
                    lessons_template_name varchar(255) not null,
                    lessons_template_description text
                );
                CREATE INDEX idx_lessons_templates on lessons_templates (lessons_template_id);
                CREATE TABLE lessons_template_categories (
                    lessons_template_category_id varchar(255) not null constraint lessons_template_categories_pkey primary key,
                    lessons_template_category_created_at timestamp not null default now(),
                    lessons_template_category_updated_at timestamp not null default now(),
                    lessons_template_category_name varchar(255) not null,
                    lessons_template_category_description text,
                    lessons_template_category_order int not null,
                    lessons_template_category_template varchar(255) not null constraint fk_lessons_template_category_template references lessons_templates on delete cascade
                );
                CREATE INDEX idx_lessons_template_categories on lessons_template_categories (lessons_template_category_id);
                CREATE INDEX idx_lessons_template_category_template on lessons_template_categories (lessons_template_category_template);
                CREATE TABLE lessons_template_questions (
                    lessons_template_question_id varchar(255) not null constraint lessons_template_questions_pkey primary key,
                    lessons_template_question_created_at timestamp not null default now(),
                    lessons_template_question_updated_at timestamp not null default now(),
                    lessons_template_question_content text not null,
                    lessons_template_question_explanation text,
                    lessons_template_question_order int not null,
                    lessons_template_question_category varchar(255) not null constraint fk_lessons_template_question_category references lessons_template_categories on delete cascade
                );
                CREATE INDEX idx_lessons_template_questions on lessons_template_questions (lessons_template_question_id);
                CREATE INDEX idx_lessons_template_question_category on lessons_template_questions (lessons_template_question_category);
        """);
    select.execute(
        """
                CREATE TABLE lessons_categories (
                    lessons_category_id varchar(255) not null constraint lessons_categories_pkey primary key,
                    lessons_category_created_at timestamp not null default now(),
                    lessons_category_updated_at timestamp not null default now(),
                    lessons_category_name varchar(255) not null,
                    lessons_category_description text,
                    lessons_category_order int not null,
                    lessons_category_exercise varchar(255) not null constraint fk_lessons_category_exercise references exercises on delete cascade
                );
                CREATE INDEX idx_lessons_categories on lessons_categories (lessons_category_id);
                CREATE INDEX idx_lessons_category_exercise on lessons_categories (lessons_category_exercise);
                CREATE TABLE lessons_questions (
                    lessons_question_id varchar(255) not null constraint lessons_questions_pkey primary key,
                    lessons_question_created_at timestamp not null default now(),
                    lessons_question_updated_at timestamp not null default now(),
                    lessons_question_content text not null,
                    lessons_question_explanation text,
                    lessons_question_order int not null,
                    lessons_question_category varchar(255) not null constraint fk_lessons_question_category references lessons_categories on delete cascade
                );
                CREATE INDEX idx_lessons_questions on lessons_questions (lessons_question_id);
                CREATE INDEX idx_lessons_question_category on lessons_questions (lessons_question_category);
                CREATE TABLE lessons_questions_audiences (
                    audience_id varchar(255) not null constraint audience_id_fk references audiences on delete cascade,
                    lessons_question_id varchar(255) not null constraint lessons_question_id_fk references lessons_questions on delete cascade,
                    constraint lessons_questions_audiences_pkey primary key (audience_id, lessons_question_id)
                );
                CREATE INDEX idx_lessons_questions_audiences_question on lessons_questions_audiences (lessons_question_id);
                CREATE INDEX idx_lessons_questions_audiences_audience on lessons_questions_audiences (audience_id);
        """);
    select.execute(
        """
                CREATE TABLE lessons_answers (
                    lessons_answer_id varchar(255) not null constraint lessons_answers_pkey primary key,
                    lessons_answer_created_at timestamp not null default now(),
                    lessons_answer_updated_at timestamp not null default now(),
                    lessons_answer_positive text,
                    lessons_answer_negative text,
                    lessons_answer_score int not null,
                    lessons_answer_question varchar(255) not null constraint fk_lessons_answer_question references lessons_questions on delete cascade,
                    lessons_answer_user varchar(255) constraint fk_lessons_answer_user references users on delete set null
                );
                CREATE INDEX idx_lessons_answers on lessons_answers (lessons_answer_id);
                CREATE INDEX idx_lessons_answer_question on lessons_answers (lessons_answer_question);
                CREATE INDEX idx_lessons_answer_user on lessons_answers (lessons_answer_user);
        """);
  }
}
