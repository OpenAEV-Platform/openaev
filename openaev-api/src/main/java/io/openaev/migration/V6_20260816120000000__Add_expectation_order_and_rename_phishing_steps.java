package io.openaev.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Phishing expectation naming + generic expectation ordering.
 *
 * <p>1. Adds the nullable {@code inject_expectation_order} column: the contract-declared display
 * order of an expectation within its inject (phishing declares email -> link -> submission as 1 ->
 * 2 -> 3). Nullable with no default, so the ALTER is metadata-only and every non-phishing
 * expectation stays unordered.
 *
 * <p>2. Renames the three phishing human-step expectations to their resisted-outcome names ("Email
 * opened" -> "Email not opened", "Phishing link clicked" -> "Link not clicked", "Credentials
 * submitted" -> "Credentials not submitted") and backfills their order. The names are the join key
 * between the persisted rows and the tracking constants in {@code PhishingTrackingService}, so
 * in-flight injects would stop scoring after the rename without this realignment. Scoped to injects
 * of the phishing injector so a user-defined manual expectation that happens to share a name is
 * never touched.
 *
 * <p>3. Rewrites the {@code expectations} array inside {@code inject_content} of phishing injects
 * the same way: pending injects re-create their expectation rows from that JSON at execution time,
 * so stale content would resurrect the old names (which the tracking service no longer matches).
 *
 * <p>Injector contract rows are deliberately NOT migrated: {@code
 * PhishingLandingPageService.resyncAllContracts()} rebuilds every landing-page contract at startup,
 * which propagates the new names and orders there.
 *
 * <p>Idempotent: the ALTER is IF NOT EXISTS, the renames match only old names, and the order
 * backfill / JSON rewrite converge to the same terminal state on re-run.
 */
@Component
public class V6_20260816120000000__Add_expectation_order_and_rename_phishing_steps
    extends BaseJavaMigration {

  private static final String PHISHING_INJECTOR_TYPE = "openaev_phishing";

  /** Old name -> new resisted-outcome name. */
  private static final Map<String, String> RENAMES =
      Map.of(
          "Email opened", "Email not opened",
          "Phishing link clicked", "Link not clicked",
          "Credentials submitted", "Credentials not submitted");

  /** New name -> contract-declared display order (email -> link -> submission). */
  private static final Map<String, Integer> ORDERS =
      Map.of(
          "Email not opened", 1,
          "Link not clicked", 2,
          "Credentials not submitted", 3);

  private static final int BATCH_SIZE = 500;

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      // 1. Generic display-order column (nullable, no default: metadata-only ALTER).
      statement.execute(
          "ALTER TABLE injects_expectations "
              + "ADD COLUMN IF NOT EXISTS inject_expectation_order int");

      // 2. Rename persisted phishing steps and backfill their order. Old names are mapped to the
      // new resisted-outcome names; rows already carrying a new name (idempotent re-run, or rows
      // written between deploy and migration) only get the order backfill.
      statement.execute(
          """
          UPDATE injects_expectations e
          SET inject_expectation_name = CASE e.inject_expectation_name
                WHEN 'Email opened' THEN 'Email not opened'
                WHEN 'Phishing link clicked' THEN 'Link not clicked'
                WHEN 'Credentials submitted' THEN 'Credentials not submitted'
                ELSE e.inject_expectation_name
              END,
              inject_expectation_order = CASE e.inject_expectation_name
                WHEN 'Email opened' THEN 1
                WHEN 'Email not opened' THEN 1
                WHEN 'Phishing link clicked' THEN 2
                WHEN 'Link not clicked' THEN 2
                WHEN 'Credentials submitted' THEN 3
                WHEN 'Credentials not submitted' THEN 3
                ELSE e.inject_expectation_order
              END
          FROM injects i
          JOIN injectors_injector_contracts iic
            ON iic.injector_contract_id = i.inject_injector_contract
          JOIN injectors inj
            ON inj.injector_id = iic.injector_id
          WHERE e.inject_id = i.inject_id
            AND inj.injector_type = 'openaev_phishing'
            AND e.inject_expectation_name IN (
              'Email opened', 'Phishing link clicked', 'Credentials submitted',
              'Email not opened', 'Link not clicked', 'Credentials not submitted')
          """);
    }

    // 3. Rewrite the expectations array in the content of phishing injects: rename old step names
    // and stamp the display order, so a pending inject executed after the upgrade creates rows the
    // tracking service can still match (and the UI can sort).
    rewriteInjectContent(context);

    try (Statement statement = context.getConnection().createStatement()) {
      // 4. Expectation names are indexed in ES: force a full re-index of expectation documents.
      statement.execute(
          "DELETE FROM indexing_status WHERE indexing_status_type = 'expectation-inject'");
    }
  }

  private void rewriteInjectContent(Context context) throws Exception {
    ObjectMapper mapper = new ObjectMapper();

    try (Statement select = context.getConnection().createStatement();
        ResultSet results =
            select.executeQuery(
                "SELECT DISTINCT i.inject_id, i.inject_content "
                    + "FROM injects i "
                    + "JOIN injectors_injector_contracts iic "
                    + "  ON iic.injector_contract_id = i.inject_injector_contract "
                    + "JOIN injectors inj ON inj.injector_id = iic.injector_id "
                    + "WHERE inj.injector_type = '"
                    + PHISHING_INJECTOR_TYPE
                    + "' AND i.inject_content IS NOT NULL");
        PreparedStatement update =
            context
                .getConnection()
                .prepareStatement("UPDATE injects SET inject_content = ? WHERE inject_id = ?")) {

      int batchCount = 0;

      while (results.next()) {
        String injectId = results.getString("inject_id");
        String content = results.getString("inject_content");
        if (content == null || content.isBlank()) {
          continue;
        }

        JsonNode root = mapper.readTree(content);
        if (root == null || !root.isObject()) {
          continue;
        }
        JsonNode expectations = root.get("expectations");
        if (expectations == null || !expectations.isArray()) {
          continue;
        }

        boolean modified = false;
        for (JsonNode expectation : expectations) {
          if (!(expectation instanceof ObjectNode expectationNode)) {
            continue;
          }
          String name = expectationNode.path("expectation_name").asText(null);
          if (name == null) {
            continue;
          }
          String newName = RENAMES.get(name);
          if (newName != null) {
            expectationNode.put("expectation_name", newName);
            name = newName;
            modified = true;
          }
          Integer order = ORDERS.get(name);
          if (order != null
              && (!expectationNode.hasNonNull("expectation_order")
                  || expectationNode.get("expectation_order").asInt() != order)) {
            expectationNode.put("expectation_order", (int) order);
            modified = true;
          }
        }

        if (modified) {
          update.setString(1, mapper.writeValueAsString(root));
          update.setString(2, injectId);
          update.addBatch();
          batchCount++;
          if (batchCount == BATCH_SIZE) {
            update.executeBatch();
            batchCount = 0;
          }
        }
      }

      if (batchCount != 0) {
        update.executeBatch();
      }
    }
  }
}
