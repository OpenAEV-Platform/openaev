package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Strips the path from document names stored before uploads were sanitized, keeping only the last
 * component as {@code DocumentService.sanitizeFileName} now does. A name with no last component
 * falls back to the default download name: the column is not null and uploads reject blank names.
 * Re-running is a no-op, since no rewritten name carries a separator or is blank.
 */
@Component
public class V6_20261015100000000__Sanitize_document_names extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      // Raw JDBC on purpose: a data repair across every tenant, on a table that is not indexed.
      statement.executeUpdate(
          "WITH sanitized AS ("
              + "  SELECT document_id, regexp_replace(document_name, '^.*[/\\\\]', '') AS name "
              + "  FROM documents "
              + "  WHERE document_name ~ '[/\\\\]' OR document_name !~ '\\S'"
              + ") "
              + "UPDATE documents d "
              + "SET document_name = CASE WHEN s.name ~ '\\S' THEN s.name ELSE 'file' END "
              + "FROM sanitized s "
              + "WHERE d.document_id = s.document_id;");
    }
  }
}
