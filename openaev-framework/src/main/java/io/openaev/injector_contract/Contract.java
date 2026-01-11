package io.openaev.injector_contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.Domain;
import io.openaev.database.model.Endpoint.PLATFORM_TYPE;
import io.openaev.helper.SupportedLanguage;
import io.openaev.injector_contract.fields.ContractElement;
import io.openaev.injector_contract.variables.VariableHelper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents an injector contract that defines the structure and configuration of an injection.
 *
 * <p>A contract specifies:
 *
 * <ul>
 *   <li>The configuration and metadata (labels, colors, type)
 *   <li>The input fields required for the injection
 *   <li>The variables available for template substitution
 *   <li>The target platforms and domains
 *   <li>Associated attack patterns (MITRE ATT&CK)
 * </ul>
 *
 * <p>Contracts can be either manual (requiring human interaction) or executable (automated). Use
 * the factory methods {@link #manualContract} and {@link #executableContract} to create instances.
 *
 * <p>Example creating an executable contract:
 *
 * <pre>{@code
 * Contract emailContract = Contract.executableContract(
 *     config,
 *     "email-send",
 *     Map.of(SupportedLanguage.en, "Send Email"),
 *     ContractDef.contractBuilder()
 *         .mandatory(ContractText.textField("subject", "Subject"))
 *         .mandatory(ContractTextArea.richTextareaField("body", "Body"))
 *         .build(),
 *     List.of(PLATFORM_TYPE.Generic),
 *     false,
 *     Set.of(communicationDomain)
 * );
 * }</pre>
 *
 * @see ContractConfig
 * @see ContractElement
 * @see ContractVariable
 */
@Getter
public class Contract {

  /** The configuration containing injector metadata like type, colors, and labels. */
  @NotNull private final ContractConfig config;

  /** Unique identifier for this contract. */
  @NotBlank
  @Setter
  @JsonProperty("contract_id")
  private String id;

  /** Localized labels for this contract, keyed by language. */
  @NotEmpty @Setter private Map<SupportedLanguage, String> label;

  /** Whether this contract requires manual execution. */
  private final boolean manual;

  /** The input fields that define the contract's form structure. */
  @NotEmpty @Setter private List<ContractElement> fields;

  /** Variables available for template substitution in this contract. */
  private final List<ContractVariable> variables = new ArrayList<>();

  /** Additional context data for the contract execution. */
  private final Map<String, String> context = new HashMap<>();

  /** External IDs of associated MITRE ATT&CK patterns. */
  @Setter
  @JsonProperty("contract_attack_patterns_external_ids")
  private List<String> attackPatternsExternalIds = new ArrayList<>();

  /** Whether this contract can be used for atomic testing. */
  @Setter
  @JsonProperty("is_atomic_testing")
  private boolean isAtomicTesting = true;

  /** Whether this contract requires an executor agent. */
  @Setter
  @JsonProperty("needs_executor")
  private boolean needsExecutor = false;

  /** The platforms this contract is compatible with. */
  @Setter
  @JsonProperty("platforms")
  private List<PLATFORM_TYPE> platforms = new ArrayList<>();

  /** The domains this contract belongs to. */
  @Setter
  @JsonProperty("domains")
  private Set<Domain> domains;

  private Contract(
      @NotNull final ContractConfig config,
      @NotBlank final String id,
      @NotEmpty final Map<SupportedLanguage, String> label,
      final boolean manual,
      @NotEmpty final List<ContractElement> fields,
      final List<PLATFORM_TYPE> platforms,
      final boolean needsExecutor,
      final Set<Domain> domains) {
    this.config = Objects.requireNonNull(config, "Contract config cannot be null");
    this.id = Objects.requireNonNull(id, "Contract id cannot be null");
    this.label = Objects.requireNonNull(label, "Contract label cannot be null");
    this.manual = manual;
    this.fields = Objects.requireNonNull(fields, "Contract fields cannot be null");
    this.needsExecutor = needsExecutor;
    this.platforms = platforms != null ? platforms : new ArrayList<>();
    this.domains = domains != null ? domains : new HashSet<>();

    // Add default variables linked to ExecutionContext
    this.variables.add(VariableHelper.userVariable);
    this.variables.add(VariableHelper.exerciseVariable);
    this.variables.add(VariableHelper.teamVariable);
    this.variables.addAll(VariableHelper.uriVariables);
  }

  /**
   * Creates a manual contract that requires human interaction for execution.
   *
   * <p>Manual contracts are not eligible for atomic testing by default.
   *
   * @param config the contract configuration
   * @param id unique identifier for the contract
   * @param label localized labels for the contract
   * @param fields input fields for the contract form
   * @param platforms target platforms (defaults to Generic if null)
   * @param needsExecutor whether an executor agent is required
   * @param domains the domains this contract belongs to
   * @return a new manual Contract instance
   */
  public static Contract manualContract(
      @NotNull final ContractConfig config,
      @NotBlank final String id,
      @NotEmpty final Map<SupportedLanguage, String> label,
      @NotEmpty final List<ContractElement> fields,
      final List<PLATFORM_TYPE> platforms,
      final boolean needsExecutor,
      final Set<Domain> domains) {
    Contract contract =
        new Contract(
            config,
            id,
            label,
            true,
            fields,
            platforms == null ? List.of(PLATFORM_TYPE.Generic) : platforms,
            needsExecutor,
            domains);
    contract.setAtomicTesting(false);
    return contract;
  }

  /**
   * Creates an executable contract that can be automated.
   *
   * <p>Executable contracts are eligible for atomic testing by default.
   *
   * @param config the contract configuration
   * @param id unique identifier for the contract
   * @param label localized labels for the contract
   * @param fields input fields for the contract form
   * @param platforms target platforms (defaults to Generic if null)
   * @param needsExecutor whether an executor agent is required
   * @param domains the domains this contract belongs to
   * @return a new executable Contract instance
   */
  public static Contract executableContract(
      @NotNull final ContractConfig config,
      @NotBlank final String id,
      @NotEmpty final Map<SupportedLanguage, String> label,
      @NotEmpty final List<ContractElement> fields,
      final List<PLATFORM_TYPE> platforms,
      final boolean needsExecutor,
      final Set<Domain> domains) {
    return new Contract(
        config,
        id,
        label,
        false,
        fields,
        platforms == null ? List.of(PLATFORM_TYPE.Generic) : platforms,
        needsExecutor,
        domains);
  }

  /**
   * Adds a context key-value pair for use during contract execution.
   *
   * @param key the context key
   * @param value the context value
   */
  public void addContext(String key, String value) {
    if (key != null && value != null) {
      this.context.put(key, value);
    }
  }

  /**
   * Adds a variable at the beginning of the variables list.
   *
   * <p>Variables added via this method take precedence over default variables with the same key.
   *
   * @param variable the variable to add
   */
  public void addVariable(ContractVariable variable) {
    if (variable != null) {
      variables.add(0, variable);
    }
  }

  /**
   * Associates a MITRE ATT&CK pattern with this contract by its external ID.
   *
   * @param externalId the external ID of the attack pattern (e.g., "T1566.001")
   */
  public void addAttackPattern(String externalId) {
    if (externalId != null && !externalId.isBlank()) {
      attackPatternsExternalIds.add(externalId);
    }
  }
}
