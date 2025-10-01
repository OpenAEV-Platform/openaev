package io.openaev.rest.inject.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/** Represent the supported fields that can be bulk updated in injects */
@Getter
public enum InjectBulkUpdateSupportedFields {
  @JsonProperty("assets")
  ASSETS("assets"),
  @JsonProperty("asset_groups")
  ASSET_GROUPS("assetGroups"),
  @JsonProperty("teams")
  TEAMS("teams");

  private final String value;

  InjectBulkUpdateSupportedFields(final String value) {
    this.value = value;
  }
}
