package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_75__Add_table_injects_expectations_traces extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();
    select.execute(
        """
                    CREATE TABLE injects_expectations_traces(
                        inject_expectation_trace_id VARCHAR(255) NOT NULL CONSTRAINT injects_expectations_traces_pkey PRIMARY KEY,
                        inject_expectation_trace_expectation VARCHAR(255) NOT NULL CONSTRAINT injects_expectations_traces_expectation_fkey REFERENCES injects_expectations (inject_expectation_id) ON DELETE CASCADE,
                        inject_expectation_trace_source_id VARCHAR(255) NOT NULL CONSTRAINT inject_expectation_trace_source_id_fk REFERENCES assets (asset_id) ON DELETE CASCADE,
                        inject_expectation_trace_alert_name text,
                        inject_expectation_trace_alert_link text,
                        inject_expectation_trace_date timestamp(0) with time zone,
                        inject_expectation_trace_created_at timestamp(0) with time zone not null default now(),
                        inject_expectation_trace_updated_at timestamp(0) with time zone not null default now()
                    );
                    CREATE INDEX idx_inject_expectation_trace_expectation ON injects_expectations_traces(inject_expectation_trace_expectation);
                    CREATE INDEX idx_inject_expectation_trace_source_id ON injects_expectations_traces(inject_expectation_trace_source_id);
            """);
  }
}
