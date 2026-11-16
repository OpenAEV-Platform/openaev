package io.openaev.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/** Moves the tokens typed in AI red-team injects into AI targets, and out of the inject content. */
@Slf4j
@Component
public class V6_20261116180000000__Move_inline_ai_target_tokens extends BaseJavaMigration {

  private static final String TOKEN = "target_token";
  private static final String SELECTOR = "target_selector";
  private static final String AI_TARGET = "ai_target";
  private static final String MANUAL = "manual";
  private static final String DEFAULT_PROVIDER = "OPENAI_COMPATIBLE";

  private record Target(
      String tenantId, String provider, String endpoint, String model, String token) {}

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    ObjectMapper mapper = new ObjectMapper();
    Map<Target, String> aiTargetIds = new HashMap<>();

    try (Statement select = connection.createStatement();
        ResultSet injects =
            select.executeQuery(
                "SELECT inject_id, tenant_id, inject_content FROM injects"
                    + " WHERE inject_content::jsonb ? '"
                    + TOKEN
                    + "'");
        PreparedStatement update =
            connection.prepareStatement(
                "UPDATE injects SET inject_content = CAST(? AS jsonb) WHERE inject_id = ?")) {
      while (injects.next()) {
        String injectId = injects.getString("inject_id");
        ObjectNode content = (ObjectNode) mapper.readTree(injects.getString("inject_content"));
        String token = content.path(TOKEN).asText("");
        if (!token.isBlank() && isManual(content)) {
          Target target =
              new Target(
                  injects.getString("tenant_id"),
                  firstText(content.get("target_provider"), DEFAULT_PROVIDER),
                  firstText(content.get("target_endpoint"), null),
                  firstText(content.get("target_model"), null),
                  token);
          String aiTargetId =
              aiTargetIds.computeIfAbsent(target, t -> findOrCreateAiTarget(connection, t));
          content.set(
              SELECTOR,
              content.path(SELECTOR).isArray()
                  ? mapper.valueToTree(List.of(AI_TARGET))
                  : mapper.getNodeFactory().textNode(AI_TARGET));
          content.put(AI_TARGET, aiTargetId);
          log.info("Inject {} now targets AI target {}", injectId, aiTargetId);
        }
        content.remove(TOKEN);
        update.setString(1, mapper.writeValueAsString(content));
        update.setString(2, injectId);
        update.addBatch();
      }
      update.executeBatch();
    }
  }

  private static boolean isManual(ObjectNode content) {
    String selector = firstText(content.get(SELECTOR), null);
    return MANUAL.equals(selector) || (selector == null && !content.hasNonNull(AI_TARGET));
  }

  private static String firstText(JsonNode value, String fallback) {
    JsonNode node = value != null && value.isArray() ? value.path(0) : value;
    String text = node == null || node.isNull() ? "" : node.asText("");
    return text.isBlank() ? fallback : text;
  }

  private static String findOrCreateAiTarget(Connection connection, Target target) {
    try (PreparedStatement find =
        connection.prepareStatement(
            "SELECT asset_id FROM assets WHERE tenant_id = ? AND asset_category = 'AI_TARGET'"
                + " AND ai_target_provider = ? AND ai_target_endpoint IS NOT DISTINCT FROM ?"
                + " AND ai_target_model IS NOT DISTINCT FROM ? AND ai_target_token = ? LIMIT 1")) {
      find.setString(1, target.tenantId());
      find.setString(2, target.provider());
      find.setString(3, target.endpoint());
      find.setString(4, target.model());
      find.setString(5, target.token());
      try (ResultSet found = find.executeQuery()) {
        if (found.next()) {
          return found.getString(1);
        }
      }
      String id = UUID.randomUUID().toString();
      try (PreparedStatement insert =
          connection.prepareStatement(
              "INSERT INTO assets (asset_id, asset_type, asset_name, asset_category, tenant_id,"
                  + " ai_target_provider, ai_target_endpoint, ai_target_model, ai_target_modality,"
                  + " ai_target_configuration, ai_target_token, asset_metadata,"
                  + " asset_created_at, asset_updated_at)"
                  + " VALUES (?, 'Asset', ?, 'AI_TARGET', ?, ?, ?, ?, 'TEXT', '{}'::jsonb, ?,"
                  + " '{}'::jsonb, now(), now())")) {
        insert.setString(1, id);
        insert.setString(2, name(target));
        insert.setString(3, target.tenantId());
        insert.setString(4, target.provider());
        insert.setString(5, target.endpoint());
        insert.setString(6, target.model());
        insert.setString(7, target.token());
        insert.executeUpdate();
      }
      log.info("Created AI target {} for a token typed in an inject", id);
      return id;
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String name(Target target) {
    String host = target.endpoint();
    try {
      if (host != null && URI.create(host).getHost() != null) {
        host = URI.create(host).getHost();
      }
    } catch (IllegalArgumentException ignored) {
      // keeps the endpoint as typed
    }
    String model = target.model() == null ? target.provider() : target.model();
    return host == null ? model : model + " - " + host;
  }
}
