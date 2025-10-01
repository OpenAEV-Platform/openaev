package io.openaev.injector_contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.Endpoint.PLATFORM_TYPE;
import io.openaev.helper.SupportedLanguage;
import io.openaev.injector_contract.fields.ContractElement;
import io.openaev.injector_contract.variables.VariableHelper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
public class Contract {

  @NotNull private final ContractConfig config;

  @NotBlank
  @Setter
  @JsonProperty("contract_id")
  private String id;

  @NotEmpty @Setter private Map<SupportedLanguage, String> label;

  @NotNull private final boolean manual;

  @NotEmpty @Setter private List<ContractElement> fields;

  @NotEmpty private final List<ContractVariable> variables = new ArrayList<>();

  @NotNull private final Map<String, String> context = new HashMap<>();

  @NotEmpty
  @Setter
  @JsonProperty("contract_attack_patterns_external_ids")
  private List<String> attackPatternsExternalIds = new ArrayList<>();

  @Setter
  @JsonProperty("is_atomic_testing")
  private boolean isAtomicTesting = true;

  @Setter
  @JsonProperty("needs_executor")
  private boolean needsExecutor = false;

  @Setter
  @JsonProperty("platforms")
  private List<PLATFORM_TYPE> platforms = new ArrayList<>();

  private Contract(
      @NotNull final ContractConfig config,
      @NotBlank final String id,
      @NotEmpty final Map<SupportedLanguage, String> label,
      final boolean manual,
      @NotEmpty final List<ContractElement> fields,
      final List<PLATFORM_TYPE> platforms,
      final boolean needsExecutor) {
    this.config = config;
    this.id = id;
    this.label = label;
    this.manual = manual;
    this.fields = fields;
    this.needsExecutor = needsExecutor;
    this.platforms = platforms;

    // Default variables linked to ExecutionContext
    // User variables
    this.variables.add(VariableHelper.userVariable);
    // Exercise variables
    this.variables.add(VariableHelper.exerciceVariable);
    // Teams
    this.variables.add(VariableHelper.teamVariable);
    // Direct uris
    this.variables.addAll(VariableHelper.uriVariables);
  }

  public static Contract manualContract(
      @NotNull final ContractConfig config,
      @NotBlank final String id,
      @NotEmpty final Map<SupportedLanguage, String> label,
      @NotEmpty final List<ContractElement> fields,
      final List<PLATFORM_TYPE> platforms,
      final boolean needsExecutor) {
    Contract contract =
        new Contract(
            config,
            id,
            label,
            true,
            fields,
            platforms == null ? List.of(PLATFORM_TYPE.Generic) : platforms,
            needsExecutor);
    contract.setAtomicTesting(false);
    return contract;
  }

  public static Contract executableContract(
      @NotNull final ContractConfig config,
      @NotBlank final String id,
      @NotEmpty final Map<SupportedLanguage, String> label,
      @NotEmpty final List<ContractElement> fields,
      final List<PLATFORM_TYPE> platforms,
      final boolean needsExecutor) {
    return new Contract(
        config,
        id,
        label,
        false,
        fields,
        platforms == null ? List.of(PLATFORM_TYPE.Generic) : platforms,
        needsExecutor);
  }

  public void addContext(String key, String value) {
    this.context.put(key, value);
  }

  public void addVariable(ContractVariable variable) {
    variables.add(0, variable);
  }

  public void addAttackPattern(String id) {
    attackPatternsExternalIds.add(id);
  }
}
