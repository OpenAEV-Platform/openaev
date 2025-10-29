package io.openaev.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

import java.sql.Statement;

@Component
public class V4_46__Implement_Domains_notion extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        try (Statement stmt = context.getConnection().createStatement()) {
            stmt.execute(
                    """
                                CREATE TABLE domains (
                                    domain_id VARCHAR(255) NOT NULL CONSTRAINT domains_pkey PRIMARY KEY,
                                    domain_name VARCHAR(255) NOT NULL UNIQUE,
                                    domain_color VARCHAR(255) NOT NULL DEFAULT '#FFFFFF',
                                    domain_created_at TIMESTAMPTZ DEFAULT now(),
                                    domain_updated_at TIMESTAMPTZ DEFAULT now()
                                );
                            """);

            stmt.execute(
                    """
                                CREATE TABLE payloads_domains (
                                    payload_id VARCHAR(255) NOT NULL,
                                    domain_id VARCHAR(255) NOT NULL,
                                    PRIMARY KEY (payload_id, domain_id),
                                    CONSTRAINT fk_payloads_domains_domain FOREIGN KEY (domain_id) REFERENCES domains(domain_id) ON DELETE CASCADE,
                                    CONSTRAINT fk_payloads_domains_payload FOREIGN KEY (payload_id) REFERENCES payloads(payload_id) ON DELETE CASCADE
                                );
                            """);

            stmt.execute("CREATE INDEX idx_payloads_domains_domain_id ON payloads_domains(domain_id);");
            stmt.execute("CREATE INDEX idx_payloads_domains_payload_id ON payloads_domains(payload_id);");
        }
    }
}
