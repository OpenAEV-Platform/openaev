package io.openaev.rest.mitigation.form;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MitigationCreateInput {

  @JsonProperty("mitigation_stix_id")
  private String stixId = "course-of-action--" + UUID.randomUUID();

  ;

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("mitigation_name")
  private String name;

  @JsonProperty("mitigation_description")
  private String description;

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("mitigation_external_id")
  private String externalId;

  @JsonProperty("mitigation_log_sources")
  private String[] logSources = new String[0];

  @JsonProperty("mitigation_threat_hunting_techniques")
  private String threatHuntingTechniques;

  @JsonProperty("mitigation_attack_patterns")
  private List<String> attackPatternsIds = new ArrayList<>();
}
