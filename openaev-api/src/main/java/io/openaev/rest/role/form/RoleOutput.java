package io.openaev.rest.role.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import java.util.Set;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

@Builder(toBuilder = true)
@Getter
@Jacksonized
@EqualsAndHashCode
public class RoleOutput {
  @JsonProperty("role_id")
  @NotBlank
  private String id;

  @JsonProperty("role_name")
  @NotBlank
  private String name;

  @JsonProperty("role_description")
  private String description;

  @JsonProperty("role_capabilities")
  private Set<String> capabilities;

  @JsonProperty("role_created_at")
  private String createdAt;

  @JsonProperty("role_updated_at")
  private String updatedAt;
}
