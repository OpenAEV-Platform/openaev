package io.openbas.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_35__Clean_agent_side_on_inject_expectation_widget extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Statement select = context.getConnection().createStatement();
    select.executeUpdate(
        "DELETE FROM indexing_status WHERE indexing_status_type in ( 'expectation-inject');");

    ResultSet results =
        select.executeQuery(
            "SELECT widget_id, widget_config FROM widgets WHERE widget_type != 'SECURITY_COVERAGE_CHART' AND widget_type != 'ATTACK_PATH'");

    PreparedStatement statement =
        context
            .getConnection()
            .prepareStatement("UPDATE widgets SET widget_config = ?::jsonb WHERE widget_id=?");

    ObjectMapper mapper = new ObjectMapper();

    while (results.next()) {
      String configStr = results.getString("widget_config");
      ObjectNode config = mapper.readValue(configStr, ObjectNode.class);
      String widgetConfigType = config.get("widget_configuration_type").asText();
      ArrayNode series;
      if ("list".equals(widgetConfigType)) {
        ObjectMapper objectMapper = new ObjectMapper();
        series = objectMapper.createArrayNode();
        series.add(config.get("perspective"));
      } else {
        series = (ArrayNode) config.get("series");
      }

      boolean changeMade = false;
      // Remove base_agent_side filter if expectation-inject is present
      for (JsonNode serie : series) {
        ArrayNode filters = (ArrayNode) serie.get("filter").get("filters");
        boolean hasExpectationInject = false;
        int indexToRemove = -1;
        for (int i = 0; i < filters.size(); i++) {
          JsonNode filter = filters.get(i);
          String key = filter.get("key").asText();
          if ("base_entity".equals(key)
              && "expectation-inject".equals(filter.get("values").get(0).asText())) {
            hasExpectationInject = true;
          }
          if ("base_agent_side".equals(key)) {
            indexToRemove = i;
          }
        }
        if (hasExpectationInject && indexToRemove >= 0) {
          filters.remove(indexToRemove);
          changeMade = true;
        }
      }
      // Change base_agent_side to base_asset_side in field if exists
      if (config.has("field") && "base_agent_side".equals(config.get("field").asText())) {
        config.put("field", "base_asset_side");
        changeMade = true;
      }
      // remove base_agent_side sort if exists
      if (config.has("sorts")) {
        ArrayNode sorts = (ArrayNode) config.get("sorts");
        for (JsonNode node : sorts) {
          ObjectNode sortNode = (ObjectNode) node;
          String fieldName = sortNode.get("fieldName").asText();
          if ("base_agent_side".equals(fieldName)) {
            sortNode.put("fieldName", "base_asset_side");
            changeMade = true;
          }
        }
      }

      if (changeMade) {
        String widgetId = results.getString("widget_id");
        String confToSave = mapper.writeValueAsString(config);
        statement.setString(1, confToSave);
        statement.setString(2, widgetId);
        statement.addBatch();
      }
    }
    statement.executeBatch();
  }
}
