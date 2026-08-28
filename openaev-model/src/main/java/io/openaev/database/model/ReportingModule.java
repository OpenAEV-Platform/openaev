package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single building block of a {@link Reporting} template, stored as a JSONB array element on the
 * reporting row. Modules are rendered in the order they appear in the array.
 */
@Getter
@Setter
@NoArgsConstructor
public class ReportingModule {

  @JsonProperty("module_type")
  private ReportingModuleType moduleType;

  /** Optional display title overriding the default title of the module type. */
  @JsonProperty("module_title")
  private String moduleTitle;

  /** Free-form module configuration (e.g. {@code content} for CUSTOM_MARKDOWN). */
  @JsonProperty("module_config")
  private Map<String, Object> moduleConfig;
}
