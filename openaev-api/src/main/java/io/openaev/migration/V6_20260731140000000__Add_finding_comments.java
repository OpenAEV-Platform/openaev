package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V6_20260731140000000__Add_finding_comments extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
              CREATE TABLE finding_comments (
                  finding_comment_id varchar(255) NOT NULL CONSTRAINT finding_comments_pkey PRIMARY KEY,
                  finding_comment_finding_id VARCHAR(255) NOT NULL CONSTRAINT finding_comment_finding_id_fk REFERENCES findings(finding_id) ON DELETE CASCADE,
                  finding_comment_author_id VARCHAR(255) NOT NULL CONSTRAINT finding_comment_author_id_fk REFERENCES users(user_id) ON DELETE CASCADE,
                  -- 4000 chars (~700-800 words) is a deliberate new baseline for this feature, not
                  -- reused from elsewhere: no existing precedent for max-length on free-text columns
                  -- exists in this codebase (cve_description, tenant_description, etc. are all
                  -- unbounded TEXT because they are system-populated or short-form). Comments are
                  -- free-form and user-facing/collaborative, so a cap guards against accidental
                  -- paste of huge content. Column stays TEXT (not VARCHAR) to stay consistent with
                  -- the rest of the codebase's free-text columns; the limit is enforced via CHECK.
                  finding_comment_content TEXT NOT NULL CONSTRAINT finding_comment_content_length_chk CHECK (char_length(finding_comment_content) <= 4000),
                  tenant_id VARCHAR(255) NOT NULL CONSTRAINT finding_comment_tenant_id_fk REFERENCES tenants(tenant_id) ON DELETE CASCADE,
                  finding_comment_created_at TIMESTAMP DEFAULT now(),
                  finding_comment_updated_at TIMESTAMP
              );
              CREATE INDEX idx_finding_comments_finding_id ON finding_comments (finding_comment_finding_id);
              CREATE INDEX idx_finding_comments_author_id ON finding_comments (finding_comment_author_id);
              CREATE INDEX idx_finding_comments_tenant_id ON finding_comments (tenant_id);
              """);
    }
  }
}
