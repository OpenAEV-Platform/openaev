package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_93__Payloads extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();
    // Purge
    select.execute("TRUNCATE attack_patterns CASCADE;");
    select.execute("TRUNCATE kill_chain_phases CASCADE;");

    // Create table payloads
    select.execute(
        """
        CREATE TABLE IF NOT EXISTS payloads (
            payload_id varchar(255) not null constraint payloads_pkey primary key,
            payload_type varchar(255) not null,
            payload_name varchar(255) not null,
            payload_description text,
            payload_content text,
            payload_created_at timestamp not null default now(),
            payload_updated_at timestamp not null default now()
        );
        CREATE INDEX IF NOT EXISTS idx_payloads on payloads (payload_id);
        """);
    // Add association table between payload and tag
    select.execute(
        """
        CREATE TABLE payloads_tags (
          payload_id varchar(255) not null constraint payload_id_fk references payloads,
          tag_id varchar(255) not null constraint tag_id_fk references tags,
          constraint payloads_tags_pkey primary key (payload_id, tag_id)
        );
        CREATE INDEX idx_payloads_tags_payload on payloads_tags (payload_id);
        CREATE INDEX idx_payloads_tags_tag on payloads_tags (tag_id);
        """);
    // Add association table between payload and inject
    select.execute(
        """
        CREATE TABLE injects_payloads (
          inject_id varchar(255) not null constraint inject_id_fk references injects on delete cascade,
          payload_id varchar(255) not null constraint payload_id_fk references payloads on delete cascade,
          constraint injects_payloads_pkey primary key (inject_id, payload_id)
        );
        CREATE INDEX idx_injects_payloads_inject on injects_payloads (inject_id);
        CREATE INDEX idx_injects_payloads_payload on injects_payloads (payload_id);
        """);
  }
}
