package io.openaev.rest.injector_contract.output;

import static java.util.Optional.ofNullable;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.*;
import io.openaev.database.model.Endpoint.PLATFORM_TYPE;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
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

  @Schema(
      description = "Map of injector ID to injector name for all injectors linked to this contract")
  @JsonProperty("injector_contract_injector_names")
  private Map<String, String> injectorNames;

  @Schema(description = "Attack pattern IDs")
  @JsonProperty("injector_contract_attack_patterns")
  private List<String> attackPatterns;

  @NotEmpty
  @Schema(description = "Domain IDs")
  @JsonProperty("injector_contract_domains")
  private List<String> domains;

  @JsonProperty("injector_contract_arch")
  private Payload.PAYLOAD_EXECUTION_ARCH arch;

  @Schema(description = "Injector IDs linked to this contract")
  @JsonProperty("injector_contract_injectors")
  private List<String> injectorIds;

  public InjectorContractFullOutput(
      String id,
      String externalId,
      Map<String, String> labels,
      String content,
      PLATFORM_TYPE[] platforms,
      String payloadType,
      String collectorType,
      String injectorType,
      String[] attackPatterns,
      List<String> domains,
      Instant updatedAt,
      Payload.PAYLOAD_EXECUTION_ARCH arch,
      Map<String, String> injectorNames) {
    super(id, externalId, updatedAt);
    this.setLabels(labels);
    this.setContent(content);
    this.setPlatforms(platforms);
    this.setPayloadType(ofNullable(collectorType).orElse(payloadType));
    this.setInjectorType(injectorType);
    this.setAttackPatterns(
        attackPatterns != null
            ? new ArrayList<>(Arrays.asList(attackPatterns))
            : new ArrayList<>());
    this.setDomains(domains != null ? new ArrayList<>(domains) : new ArrayList<>());
    this.setArch(arch);
    this.setInjectorNames(
        injectorNames != null ? new LinkedHashMap<>(injectorNames) : new LinkedHashMap<>());
    this.setInjectorIds(new ArrayList<>(this.getInjectorNames().keySet()));
    this.setHasFullDetails(true);
  }

  public static InjectorContractFullOutput fromInjectorContract(InjectorContract sourceContract) {
    Map<String, String> injectorNamesMap =
        sourceContract.getInjectors() != null
            ? sourceContract.getInjectors().stream()
                .collect(
                    Collectors.toMap(
                        Injector::getId, Injector::getName, (a, b) -> a, LinkedHashMap::new))
            : new LinkedHashMap<>();

    return new InjectorContractFullOutput(
        sourceContract.getId(),
        sourceContract.getExternalId(),
        sourceContract.getLabels(),
        sourceContract.getContent(),
        sourceContract.getPlatforms(),
        sourceContract.getPayload() == null ? null : sourceContract.getPayload().getType(),
        null,
        sourceContract.getInjectorType(),
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
        sourceContract.getPayload() == null ? null : sourceContract.getPayload().getExecutionArch(),
        injectorNamesMap);
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
