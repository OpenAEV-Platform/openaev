package io.openaev.rest.injector_contract.output;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.Endpoint;
import io.openaev.rest.payload.output.PayloadResult;
import io.openaev.rest.payload.output.PayloadSimple;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import java.time.Instant;
import java.util.*;
import javax.annotation.Nullable;
import lombok.Data;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Data
public class InjectorContractActionOutput extends InjectorContractBaseOutput
    implements PayloadResult {
  @Schema(description = "Labels")
  @JsonProperty("injector_contract_labels")
  private Map<String, String> labels;

  @Schema(description = "Payload attached")
  @JsonProperty("injector_contract_payload")
  private PayloadSimple payload;

  @Schema(description = "Injector type")
  @JsonProperty("injector_contract_injector_type")
  private String injectorType;

  @NotEmpty
  @Schema(description = "Domain IDs")
  @JsonProperty("injector_contract_domains")
  private Set<String> domains;

  @NotEmpty
  @Schema(description = "Attack Patterns IDs")
  @JsonProperty("injector_contract_attack_patterns")
  private Set<String> attackPatterns;

  @Schema(description = "Platforms")
  @JsonProperty("injector_contract_platforms")
  private Endpoint.PLATFORM_TYPE[] platforms;

  @Schema(description = "Tags Ids")
  @JsonProperty("injector_contract_tags")
  private Set<String> tags;

  public InjectorContractActionOutput(
      String id,
      String externalId,
      Instant updatedAt,
      Map<String, String> labels,
      String injectorType,
      String[] domains,
      Endpoint.PLATFORM_TYPE[] platforms,
      List<String> tags,
      @Nullable PayloadSimple payloadSimple,
      String[] attackPatterns) {
    super(id, externalId, updatedAt);
    this.setLabels(labels);
    this.setInjectorType(injectorType);
    this.setDomains(domains != null ? new HashSet<>(Arrays.asList(domains)) : new HashSet<>());
    this.setPlatforms(platforms);
    this.setTags(tags != null ? new HashSet<>(tags) : new HashSet<>());
    this.setPayload(payloadSimple);
    this.setAttackPatterns(
        attackPatterns != null ? new HashSet<>(Arrays.asList(attackPatterns)) : new HashSet<>());
  }
}
