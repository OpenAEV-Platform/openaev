package io.openaev.rest.settings.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class SettingsMcpUpdateInput {

  @JsonProperty("platform_mcp_enabled")
  @Schema(description = "Whether the MCP server is enabled")
  private boolean platformMcpEnabled;
}
