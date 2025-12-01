package io.openaev.rest.injector_contract.output;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.*;
import io.openaev.database.model.Endpoint.PLATFORM_TYPE;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.time.Instant;
import java.util.*;
import lombok.Data;

@Data
public class InjectorContractFullOutput extends InjectorContractBaseOutput {
  @Schema(description = "Labels")
  @JsonProperty("injector_contract_labels")
  private Map<String, String> labels;

  @Schema(description = "Content")
  @JsonProperty("injector_contract_content")
  @NotBlank
  private String content;

  @Schema(description = "Platforms")
  @JsonProperty("injector_contract_platforms")
  private Endpoint.PLATFORM_TYPE[] platforms;

  @Schema(description = "Payload type")
  @JsonProperty("injector_contract_payload_type")
  private String payloadType;

  @Schema(description = "Injector type")
  @JsonProperty("injector_contract_injector_type")
  private String injectorType;

  @Schema(description = "Injector name")
  @JsonProperty("injector_contract_injector_name")
  private String injectorName;

  @Schema(description = "Attack pattern IDs")
  @JsonProperty("injector_contract_attack_patterns")
  private List<String> attackPatterns;

  @NotEmpty
  @Schema(description = "Domain IDs")
  @JsonProperty("injector_contract_domains")
  private List<String> domains;

  @JsonProperty("injector_contract_arch")
  private Payload.PAYLOAD_EXECUTION_ARCH arch;

  public InjectorContractFullOutput(
      String id,
      String externalId,
      Map<String, String> labels,
      String content,
      PLATFORM_TYPE[] platforms,
      String payloadType,
      String injectorName,
      String collectorType,
      String injectorType,
      String[] attackPatterns,
      List<String> domains,
      Instant updatedAt,
      Payload.PAYLOAD_EXECUTION_ARCH arch) {
    super(id, externalId, updatedAt);
    this.setLabels(labels);
    this.setContent(content);
    this.setPlatforms(platforms);
    this.setPayloadType(Optional.ofNullable(collectorType).orElse(payloadType));
    this.setInjectorName(injectorName);
    this.setInjectorType(injectorType);
    this.setAttackPatterns(
        attackPatterns != null
            ? new ArrayList<>(Arrays.asList(attackPatterns))
            : new ArrayList<>());
    this.setDomains(domains != null ? new ArrayList<>(domains) : new ArrayList<>());

    this.setArch(arch);
    this.setHasFullDetails(true);
  }

  public static InjectorContractFullOutput fromInjectorContract(InjectorContract sourceContract) {
    return new InjectorContractFullOutput(
        sourceContract.getId(),
        sourceContract.getExternalId(),
        sourceContract.getLabels(),
        sourceContract.getContent(),
        sourceContract.getPlatforms(),
        sourceContract.getPayload() == null ? null : sourceContract.getPayload().getType(),
        sourceContract.getInjector().getName(),
        null,
        sourceContract.getInjector().getType(),
        sourceContract.getAttackPatterns().stream()
            .map(AttackPattern::getId)
            .toList()
            .toArray(new String[0]),
        resolveEffectiveDomains(
            sourceContract.getDomains().stream().map(Domain::getId).toArray(String[]::new),
            sourceContract.getPayload() != null
                ? sourceContract.getPayload().getDomains().stream()
                    .map(Domain::getId)
                    .toArray(String[]::new)
                : new String[0]),
        sourceContract.getUpdatedAt(),
        sourceContract.getPayload() == null
            ? null
            : sourceContract.getPayload().getExecutionArch());
  }

  private static List<String> resolveEffectiveDomains(
      String[] injectorDomains, String[] payloadDomains) {
    String[] effectiveDomains =
        (payloadDomains != null && payloadDomains.length > 0) ? payloadDomains : injectorDomains;
    if (effectiveDomains == null) {
      return List.of();
    }
    return Arrays.stream(effectiveDomains).filter(Objects::nonNull).distinct().toList();
  }
}
