package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_93__Alter_Creation_Date_With_Time_Zone extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
                  ALTER TABLE agents ALTER COLUMN agent_created_at type timestamp with time zone using agent_created_at::timestamp with time zone;
                  ALTER TABLE agents ALTER COLUMN agent_updated_at type timestamp with time zone using agent_updated_at::timestamp with time zone;

                  ALTER TABLE articles ALTER COLUMN article_created_at type timestamp with time zone using article_created_at::timestamp with time zone;
                  ALTER TABLE articles ALTER COLUMN article_updated_at type timestamp with time zone using article_updated_at::timestamp with time zone;

                  ALTER TABLE asset_agent_jobs ALTER COLUMN asset_agent_created_at type timestamp with time zone using asset_agent_created_at::timestamp with time zone;
                  ALTER TABLE asset_agent_jobs ALTER COLUMN asset_agent_updated_at type timestamp with time zone using asset_agent_updated_at::timestamp with time zone;

                  ALTER TABLE asset_groups ALTER COLUMN asset_group_created_at TYPE timestamp with time zone USING asset_group_created_at::timestamp with time zone;
                  ALTER TABLE asset_groups ALTER COLUMN asset_group_updated_at TYPE timestamp with time zone USING asset_group_updated_at::timestamp with time zone;

                  ALTER TABLE assets ALTER COLUMN asset_created_at TYPE timestamp with time zone USING asset_created_at::timestamp with time zone;
                  ALTER TABLE assets ALTER COLUMN asset_updated_at TYPE timestamp with time zone USING asset_updated_at::timestamp with time zone;

                  ALTER TABLE attack_patterns ALTER COLUMN attack_pattern_created_at type timestamp with time zone using attack_pattern_created_at::timestamp with time zone;
                  ALTER TABLE attack_patterns ALTER COLUMN attack_pattern_updated_at type timestamp with time zone using attack_pattern_updated_at::timestamp with time zone;

                  ALTER TABLE challenge_attempts ALTER COLUMN attempt_created_at TYPE timestamp with time zone USING attempt_created_at::timestamp with time zone;
                  ALTER TABLE challenge_attempts ALTER COLUMN attempt_updated_at TYPE timestamp with time zone USING attempt_updated_at::timestamp with time zone;

                  ALTER TABLE challenges ALTER COLUMN challenge_created_at TYPE timestamp with time zone USING challenge_created_at::timestamp with time zone;
                  ALTER TABLE challenges ALTER COLUMN challenge_updated_at TYPE timestamp with time zone USING challenge_updated_at::timestamp with time zone;

                  ALTER TABLE challenges_flags ALTER COLUMN flag_created_at TYPE timestamp with time zone USING flag_created_at::timestamp with time zone;
                  ALTER TABLE challenges_flags ALTER COLUMN flag_updated_at TYPE timestamp with time zone USING flag_updated_at::timestamp with time zone;

                  ALTER TABLE channels ALTER COLUMN channel_created_at TYPE timestamp with time zone USING channel_created_at::timestamp with time zone;
                  ALTER TABLE channels ALTER COLUMN channel_updated_at TYPE timestamp with time zone USING channel_updated_at::timestamp with time zone;

                  ALTER TABLE collectors ALTER COLUMN collector_created_at TYPE timestamp with time zone USING collector_created_at::timestamp with time zone;
                  ALTER TABLE collectors ALTER COLUMN collector_updated_at TYPE timestamp with time zone USING collector_updated_at::timestamp with time zone;

                  ALTER TABLE custom_dashboards ALTER COLUMN custom_dashboard_created_at TYPE timestamp with time zone USING custom_dashboard_created_at::timestamp with time zone;
                  ALTER TABLE custom_dashboards ALTER COLUMN custom_dashboard_updated_at TYPE timestamp with time zone USING custom_dashboard_updated_at::timestamp with time zone;

                  ALTER TABLE evaluations ALTER COLUMN evaluation_created_at TYPE timestamp with time zone USING evaluation_created_at::timestamp with time zone;
                  ALTER TABLE evaluations ALTER COLUMN evaluation_updated_at TYPE timestamp with time zone USING evaluation_updated_at::timestamp with time zone;

                  ALTER TABLE execution_traces ALTER COLUMN execution_created_at TYPE timestamp with time zone USING execution_created_at::timestamp with time zone;
                  ALTER TABLE execution_traces ALTER COLUMN execution_updated_at TYPE timestamp with time zone USING execution_updated_at::timestamp with time zone;

                  ALTER TABLE executors ALTER COLUMN executor_created_at TYPE timestamp with time zone USING executor_created_at::timestamp with time zone;
                  ALTER TABLE executors ALTER COLUMN executor_updated_at TYPE timestamp with time zone USING executor_updated_at::timestamp with time zone;

                  ALTER TABLE exercises ALTER COLUMN exercise_created_at TYPE timestamp with time zone USING exercise_created_at::timestamp with time zone;
                  ALTER TABLE exercises ALTER COLUMN exercise_updated_at TYPE timestamp with time zone USING exercise_updated_at::timestamp with time zone;

                  ALTER TABLE findings ALTER COLUMN finding_created_at TYPE timestamp with time zone USING finding_created_at::timestamp with time zone;
                  ALTER TABLE findings ALTER COLUMN finding_updated_at TYPE timestamp with time zone USING finding_updated_at::timestamp with time zone;

                  ALTER TABLE import_mappers ALTER COLUMN mapper_created_at TYPE timestamp with time zone USING mapper_created_at::timestamp with time zone;
                  ALTER TABLE import_mappers ALTER COLUMN mapper_updated_at TYPE timestamp with time zone USING mapper_updated_at::timestamp with time zone;

                  ALTER TABLE injects_expectations ALTER COLUMN inject_expectation_created_at type timestamp with time zone using inject_expectation_created_at::timestamp with time zone;
                  ALTER TABLE injects_expectations ALTER COLUMN inject_expectation_updated_at type timestamp with time zone using inject_expectation_updated_at::timestamp with time zone;

                  ALTER TABLE inject_importers ALTER COLUMN importer_created_at TYPE timestamp with time zone USING importer_created_at::timestamp with time zone;
                  ALTER TABLE inject_importers ALTER COLUMN importer_updated_at TYPE timestamp with time zone USING importer_updated_at::timestamp with time zone;

                  ALTER TABLE injectors ALTER COLUMN injector_created_at TYPE timestamp with time zone USING injector_created_at::timestamp with time zone;
                  ALTER TABLE injectors ALTER COLUMN injector_updated_at TYPE timestamp with time zone USING injector_updated_at::timestamp with time zone;

                  ALTER TABLE injectors_contracts ALTER COLUMN injector_contract_created_at TYPE timestamp with time zone USING injector_contract_created_at::timestamp with time zone;
                  ALTER TABLE injectors_contracts ALTER COLUMN injector_contract_updated_at TYPE timestamp with time zone USING injector_contract_updated_at::timestamp with time zone;

                  ALTER TABLE injects ALTER COLUMN inject_created_at TYPE timestamp with time zone USING inject_created_at::timestamp with time zone;
                  ALTER TABLE injects ALTER COLUMN inject_updated_at TYPE timestamp with time zone USING inject_updated_at::timestamp with time zone;

                  ALTER TABLE injects_dependencies ALTER COLUMN dependency_created_at TYPE timestamp with time zone USING dependency_created_at::timestamp with time zone;
                  ALTER TABLE injects_dependencies ALTER COLUMN dependency_updated_at TYPE timestamp with time zone USING dependency_updated_at::timestamp with time zone;

                  ALTER TABLE injects_expectations ALTER COLUMN inject_expectation_created_at TYPE timestamp with time zone USING inject_expectation_created_at::timestamp with time zone;
                  ALTER TABLE injects_expectations ALTER COLUMN inject_expectation_updated_at TYPE timestamp with time zone USING inject_expectation_updated_at::timestamp with time zone;

                  ALTER TABLE injects_expectations_traces ALTER COLUMN inject_expectation_trace_created_at TYPE timestamp with time zone USING inject_expectation_trace_created_at::timestamp with time zone;
                  ALTER TABLE injects_expectations_traces ALTER COLUMN inject_expectation_trace_updated_at TYPE timestamp with time zone USING inject_expectation_trace_updated_at::timestamp with time zone;

                  ALTER TABLE injects_tests_statuses ALTER COLUMN status_created_at TYPE timestamp with time zone USING status_created_at::timestamp with time zone;
                  ALTER TABLE injects_tests_statuses ALTER COLUMN status_updated_at TYPE timestamp with time zone USING status_updated_at::timestamp with time zone;

                  ALTER TABLE kill_chain_phases ALTER COLUMN phase_created_at TYPE timestamp with time zone USING phase_created_at::timestamp with time zone;
                  ALTER TABLE kill_chain_phases ALTER COLUMN phase_updated_at TYPE timestamp with time zone USING phase_updated_at::timestamp with time zone;

                  ALTER TABLE lessons_answers ALTER COLUMN lessons_answer_created_at TYPE timestamp with time zone USING lessons_answer_created_at::timestamp with time zone;
                  ALTER TABLE lessons_answers ALTER COLUMN lessons_answer_updated_at TYPE timestamp with time zone USING lessons_answer_updated_at::timestamp with time zone;

                  ALTER TABLE lessons_categories ALTER COLUMN lessons_category_created_at TYPE timestamp with time zone USING lessons_category_created_at::timestamp with time zone;
                  ALTER TABLE lessons_categories ALTER COLUMN lessons_category_updated_at TYPE timestamp with time zone USING lessons_category_updated_at::timestamp with time zone;

                  ALTER TABLE lessons_questions ALTER COLUMN lessons_question_created_at TYPE timestamp with time zone USING lessons_question_created_at::timestamp with time zone;
                  ALTER TABLE lessons_questions ALTER COLUMN lessons_question_updated_at TYPE timestamp with time zone USING lessons_question_updated_at::timestamp with time zone;

                  ALTER TABLE lessons_template_categories ALTER COLUMN lessons_template_category_created_at TYPE timestamp with time zone USING lessons_template_category_created_at::timestamp with time zone;
                  ALTER TABLE lessons_template_categories ALTER COLUMN lessons_template_category_updated_at TYPE timestamp with time zone USING lessons_template_category_updated_at::timestamp with time zone;

                  ALTER TABLE lessons_template_questions ALTER COLUMN lessons_template_question_created_at TYPE timestamp with time zone USING lessons_template_question_created_at::timestamp with time zone;
                  ALTER TABLE lessons_template_questions ALTER COLUMN lessons_template_question_updated_at TYPE timestamp with time zone USING lessons_template_question_updated_at::timestamp with time zone;

                  ALTER TABLE lessons_templates ALTER COLUMN lessons_template_created_at TYPE timestamp with time zone USING lessons_template_created_at::timestamp with time zone;
                  ALTER TABLE lessons_templates ALTER COLUMN lessons_template_updated_at TYPE timestamp with time zone USING lessons_template_updated_at::timestamp with time zone;

                  ALTER TABLE logs ALTER COLUMN log_created_at TYPE timestamp with time zone USING log_created_at::timestamp with time zone;
                  ALTER TABLE logs ALTER COLUMN log_updated_at TYPE timestamp with time zone USING log_updated_at::timestamp with time zone;

                  ALTER TABLE mitigations ALTER COLUMN mitigation_created_at TYPE timestamp with time zone USING mitigation_created_at::timestamp with time zone;
                  ALTER TABLE mitigations ALTER COLUMN mitigation_updated_at TYPE timestamp with time zone USING mitigation_updated_at::timestamp with time zone;

                  ALTER TABLE objectives ALTER COLUMN objective_created_at TYPE timestamp with time zone USING objective_created_at::timestamp with time zone;
                  ALTER TABLE objectives ALTER COLUMN objective_updated_at TYPE timestamp with time zone USING objective_updated_at::timestamp with time zone;

                  ALTER TABLE organizations ALTER COLUMN organization_created_at TYPE timestamp with time zone USING organization_created_at::timestamp with time zone;
                  ALTER TABLE organizations ALTER COLUMN organization_updated_at TYPE timestamp with time zone USING organization_updated_at::timestamp with time zone;

                  ALTER TABLE output_parsers ALTER COLUMN output_parser_created_at TYPE timestamp with time zone USING output_parser_created_at::timestamp with time zone;
                  ALTER TABLE output_parsers ALTER COLUMN output_parser_updated_at TYPE timestamp with time zone USING output_parser_updated_at::timestamp with time zone;

                  ALTER TABLE regex_groups ALTER COLUMN regex_group_created_at TYPE timestamp with time zone USING regex_group_created_at::timestamp with time zone;
                  ALTER TABLE regex_groups ALTER COLUMN regex_group_updated_at TYPE timestamp with time zone USING regex_group_updated_at::timestamp with time zone;

                  ALTER TABLE payloads ALTER COLUMN payload_created_at TYPE timestamp with time zone USING payload_created_at::timestamp with time zone;
                  ALTER TABLE payloads ALTER COLUMN payload_updated_at TYPE timestamp with time zone USING payload_updated_at::timestamp with time zone;

                  ALTER TABLE reports ALTER COLUMN report_created_at TYPE timestamp with time zone USING report_created_at::timestamp with time zone;
                  ALTER TABLE reports ALTER COLUMN report_updated_at TYPE timestamp with time zone USING report_updated_at::timestamp with time zone;

                  ALTER TABLE rule_attributes ALTER COLUMN attribute_created_at TYPE timestamp with time zone USING attribute_created_at::timestamp with time zone;
                  ALTER TABLE rule_attributes ALTER COLUMN attribute_updated_at TYPE timestamp with time zone USING attribute_updated_at::timestamp with time zone;

                  ALTER TABLE scenarios ALTER COLUMN scenario_created_at TYPE timestamp with time zone USING scenario_created_at::timestamp with time zone;
                  ALTER TABLE scenarios ALTER COLUMN scenario_updated_at TYPE timestamp with time zone USING scenario_updated_at::timestamp with time zone;

                  ALTER TABLE teams ALTER COLUMN team_created_at TYPE timestamp with time zone USING team_created_at::timestamp with time zone;
                  ALTER TABLE teams ALTER COLUMN team_updated_at TYPE timestamp with time zone USING team_updated_at::timestamp with time zone;

                  ALTER TABLE tokens ALTER COLUMN token_created_at TYPE timestamp with time zone USING token_created_at::timestamp with time zone;

                  ALTER TABLE users ALTER COLUMN user_created_at TYPE timestamp with time zone USING user_created_at::timestamp with time zone;
                  ALTER TABLE users ALTER COLUMN user_updated_at TYPE timestamp with time zone USING user_updated_at::timestamp with time zone;

                  ALTER TABLE variables ALTER COLUMN variable_created_at TYPE timestamp with time zone USING variable_created_at::timestamp with time zone;
                  ALTER TABLE variables ALTER COLUMN variable_updated_at TYPE timestamp with time zone USING variable_updated_at::timestamp with time zone;

                  ALTER TABLE widgets ALTER COLUMN widget_created_at TYPE timestamp with time zone USING widget_created_at::timestamp with time zone;
                  ALTER TABLE widgets ALTER COLUMN widget_updated_at TYPE timestamp with time zone USING widget_updated_at::timestamp with time zone;
              """);
    }
  }
}
