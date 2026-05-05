package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V5_07__Add_workflow_states extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement stmt = context.getConnection().createStatement()) {
      stmt.execute(
          """
          CREATE TABLE workflow_states (
              workflow_state_id VARCHAR(255) NOT NULL,
              workflow_execution_id VARCHAR(255) NOT NULL,
              workflow_step_template_id VARCHAR(255),
              -- Column 1: Discoveries (Global) or Step Inputs & Hashes (Local)
              workflow_state_entries JSONB NOT NULL DEFAULT '{"inputs": [], "correlated": [], "hashExecution": []}',
              workflow_state_created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
              workflow_state_updated_at TIMESTAMP WITH TIME ZONE DEFAULT now(),

              CONSTRAINT workflow_states_pkey PRIMARY KEY (workflow_state_id),
              CONSTRAINT uq_workflow_state_workflow_step UNIQUE (workflow_execution_id, workflow_step_template_id),
              CONSTRAINT fk_workflow_state_workflow FOREIGN KEY (workflow_execution_id) REFERENCES workflows(workflow_id) ON DELETE CASCADE,
              CONSTRAINT fk_workflow_state_step FOREIGN KEY (workflow_step_template_id) REFERENCES steps(step_id)
          );

          -- GIN INDEXES for high-performance JSON searching
          CREATE INDEX idx_wf_state_entries_gin ON workflow_states USING GIN (workflow_state_entries jsonb_path_ops);

          -- Indexes for lookups and fast Global row access
          CREATE INDEX idx_wf_state_execution_id ON workflow_states (workflow_execution_id);
          CREATE INDEX idx_wf_state_step_template_id ON workflow_states (workflow_step_template_id);
          CREATE INDEX idx_wf_state_global_lookup ON workflow_states (workflow_execution_id) WHERE workflow_step_template_id IS NULL;
          """);
    }
  }
}
