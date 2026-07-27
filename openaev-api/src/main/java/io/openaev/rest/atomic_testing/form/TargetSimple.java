package io.openaev.rest.atomic_testing.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.utils.TargetType;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Builder
public class TargetSimple {

  @JsonProperty("target_id")
  @NotBlank
  private String id;

  @JsonProperty("target_name")
  private String name;

  @JsonProperty("target_type")
  private TargetType type;

  // Product-facing asset category (level-1 taxonomy, e.g. HOST / WEB_APPLICATION / AI_TARGET).
  // Mirrors InjectTarget.target_category so list chips can pick the same icon as detail pages.
  // Null for target types that are not assets.
  @JsonProperty("target_category")
  private String category;

  // OS platform for host-like assets (Windows / Linux / MacOS / ...). Mirrors
  // InjectTarget.target_subtype: only meaningful when the category has an OS platform.
  @JsonProperty("target_subtype")
  private String subtype;
}
