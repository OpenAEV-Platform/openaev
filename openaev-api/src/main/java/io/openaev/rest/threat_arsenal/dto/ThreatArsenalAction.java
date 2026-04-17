package io.openaev.rest.threat_arsenal.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.Endpoint;
import io.openaev.rest.injector_contract.output.InjectorContractBaseOutput;
import io.openaev.rest.payload.output.PayloadSimple;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.Data;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Data
public class ThreatArsenalAction extends InjectorContractBaseOutput {
  @Schema(description = "Labels")
  @JsonProperty("action_labels")
  private Map<String, String> labels;

  @Schema(description = "Payload attached")
  @JsonProperty("action_payload")
  private PayloadSimple payload;

  @Schema(description = "Injector type")
  @JsonProperty("action_injector_type")
  private String injectorType;

  @NotEmpty
  @Schema(description = "Domain IDs")
  @JsonProperty("action_domains_ids")
  private Set<String> domains;

  @NotEmpty
  @Schema(description = "Attack Patterns IDs")
  @JsonProperty("action_attack_patterns_ids")
  private Set<String> attackPatterns;

  @Schema(description = "Platforms")
  @JsonProperty("action_platforms")
  private Endpoint.PLATFORM_TYPE[] platforms;

  @Schema(description = "Tags Ids")
  @JsonProperty("action_tags_ids")
  private Set<String> tags;

  public ThreatArsenalAction(
      String id,
      String externalId,
      Instant updatedAt,
      Map<String, String> labels,
      String injectorType,
      String[] domains,
      Endpoint.PLATFORM_TYPE[] platforms,
      String[] tags,
      @Nullable PayloadSimple payloadSimple,
      String[] attackPatterns) {
    super(id, externalId, updatedAt);
    this.setLabels(labels);
    this.setInjectorType(injectorType);
    this.setDomains(domains != null ? new HashSet<>(Arrays.asList(domains)) : new HashSet<>());
    this.setPlatforms(platforms);
    this.setTags(tags != null ? new HashSet<>(Arrays.asList(tags)) : new HashSet<>());
    this.setPayload(payloadSimple);
    this.setAttackPatterns(
        attackPatterns != null ? new HashSet<>(Arrays.asList(attackPatterns)) : new HashSet<>());
  }
}
