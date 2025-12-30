package io.openaev.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

import java.sql.Statement;

@Component
public class V4_53__Add_workflow_step_entities extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement select = context.getConnection().createStatement()) {

      select.execute(
          """
      DO $$
      BEGIN IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'workflow_status' ) then
              CREATE TYPE workflow_status AS ENUM ('TEMPLATE', 'STOP', 'RUN', 'END');
        END IF;
       END;
      $$;
        CREATE TABLE workflows (
            workflow_id VARCHAR(255) NOT NULL CONSTRAINT workflow_pkey PRIMARY KEY,
            workflow_status workflow_status NOT NULL ,
            workflow_version INTEGER NOT NULL,
            workflow_is_edited BOOLEAN DEFAULT false,
            workflow_simulation_id VARCHAR(255) NOT NULL REFERENCES exercises(exercise_id) ON DELETE CASCADE,
            workflow_template_id VARCHAR(255) REFERENCES workflows(workflow_id),
            workflow_created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
            workflow_updated_at TIMESTAMP WITH TIME ZONE DEFAULT now()
          );
        CREATE UNIQUE INDEX IF NOT EXISTS uk_workflow_template
            ON workflows (workflow_id, workflow_simulation_id)
            WHERE workflow_status = 'TEMPLATE';
         """);

      select.execute(
          """
      DO $$
      BEGIN IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'step_action_class' ) then
              CREATE TYPE  step_action_class AS ENUM  ('INJECT_EXECUTION') ;
        END IF;
       END;
      $$;
      DO $$
      BEGIN IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'step_status' ) then
        CREATE TYPE step_status AS ENUM ('TEMPLATE', 'WAIT', 'RUN', 'END');
        END IF;
       END;
      $$;

        CREATE TABLE steps (
            step_id VARCHAR(255) NOT NULL CONSTRAINT step_pkey PRIMARY KEY,
            step_action_class step_action_class,
            step_output JSONB,
            step_input JSONB,
            step_data JSONB,
            step_limit_execution INTEGER NOT NULL,
            step_condition_executed VARCHAR(255),
            step_output_parser VARCHAR(255),
            step_status step_status,
            step_workflow_id VARCHAR(255) NOT NULL REFERENCES workflows(workflow_id) ON DELETE CASCADE,
            step_template_id VARCHAR(255) NULL REFERENCES steps(step_id),
            step_created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
            step_updated_at TIMESTAMP WITH TIME ZONE DEFAULT now()
          );
      """);

      select.execute(
          """
      DO $$
      BEGIN IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'condition_type' ) then
        CREATE TYPE condition_type AS ENUM ('AND', 'OR', 'EQ', 'NEQ', 'IS_NULL', 'IS_NOT_NULL', 'GT', 'GTE', 'LT', 'LTE', 'IN', 'NIN','AFTER','BEFORE', 'MAPPER', 'DEPEND_ON');
        END IF;
       END;
      $$;
        CREATE TABLE conditions (
            condition_id VARCHAR(255) NOT NULL CONSTRAINT condition_pkey PRIMARY KEY,
            step_from_id VARCHAR REFERENCES steps(step_id),
            condition_key VARCHAR(255),
            condition_value VARCHAR(255),
            condition_type condition_type NOT NULL,
            condition_parent_id VARCHAR(255) REFERENCES conditions(condition_id) ON DELETE CASCADE,
            step_id VARCHAR(255) NOT NULL REFERENCES steps(step_id) ON DELETE CASCADE,
            condition_created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
            condition_updated_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
            UNIQUE ( condition_key, condition_value, condition_type, condition_parent_id, step_id)
          );
        """);
    }
  }
}
