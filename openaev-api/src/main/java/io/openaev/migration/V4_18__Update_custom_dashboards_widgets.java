package io.openaev.migration;

import static org.flywaydb.core.internal.util.StringUtils.hasText;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_18__Update_custom_dashboards_widgets extends BaseJavaMigration {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Override
  public void migrate(Context context) throws Exception {
    final Connection conn = context.getConnection();

    // Use enum name instead of enum index on custom_dashboards_parameter_type
    try (Statement st = conn.createStatement()) {
      st.executeUpdate(
          "UPDATE custom_dashboards_parameters SET custom_dashboards_parameter_type = 'simulation'");
    }

    try (PreparedStatement update =
            conn.prepareStatement(
                "UPDATE widgets SET widget_config = to_json(?::json) WHERE widget_id=?");
        Statement select = conn.createStatement();
        ResultSet results = select.executeQuery("SELECT widget_config,widget_id FROM widgets;")) {
      while (results.next()) {
        String widgetId = results.getString("widget_id");
        String rawWidgetConfig = results.getString("widget_config");

        if (rawWidgetConfig == null || rawWidgetConfig.isBlank()) {
          continue;
        }

        ObjectNode widgetConfig = MAPPER.readValue(rawWidgetConfig, ObjectNode.class);
        if (widgetConfig != null) {
          String widgetType = widgetConfig.get("widget_configuration_type").asText();
          if (widgetType == null || widgetType.isBlank()) {
            continue;
          }
          String nullValue = null;
          if (widgetType.equals("temporal-histogram")) {
            widgetConfig.put("time_range", "CUSTOM");
            String dateAttribute = widgetConfig.get("field").asText();
            widgetConfig.put(
                "date_attribute", hasText(dateAttribute) ? dateAttribute : "base_updated_at");
            widgetConfig.remove("field");
          } else if (widgetType.equals("structural-histogram")
              || widgetType.equals("list")
              || widgetType.equals("flat")) {
            widgetConfig.put("time_range", "ALL_TIME");
            widgetConfig.put("start", nullValue);
            widgetConfig.put("end", nullValue);
            widgetConfig.put("date_attribute", "base_updated_at");
          }
          update.setString(1, MAPPER.writeValueAsString(widgetConfig));
          update.setString(2, widgetId);
          update.addBatch();
        }
      }
      update.executeBatch();
    }
  }
}
