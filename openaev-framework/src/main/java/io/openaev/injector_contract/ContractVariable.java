package io.openaev.injector_contract;

import io.openaev.database.model.Variable.VariableType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Getter;

/**
 * Represents a variable available for template substitution in contract content.
 *
 * <p>Contract variables enable dynamic content in injection templates. Variables can be referenced
 * using FreeMarker template syntax (e.g., {@code ${user.email}}) and are resolved at injection
 * time.
 *
 * <p>Variables support:
 *
 * <ul>
 *   <li>Hierarchical structure with parent/child relationships
 *   <li>Different data types (String, Object, etc.)
 *   <li>Single or multiple value cardinality
 * </ul>
 *
 * <p>Example creating a nested variable structure:
 *
 * <pre>{@code
 * ContractVariable userVar = ContractVariable.variable(
 *     "user", "Target User", VariableType.Object, ContractCardinality.One,
 *     List.of(
 *         ContractVariable.variable("user.email", "Email", VariableType.String, ContractCardinality.One),
 *         ContractVariable.variable("user.name", "Name", VariableType.String, ContractCardinality.One)
 *     )
 * );
 * }</pre>
 *
 * @see ContractCardinality
 * @see VariableType
 * @see io.openaev.injector_contract.variables.VariableHelper for predefined variables
 */
@Getter
public class ContractVariable {

  /** Unique key used to reference this variable in templates (e.g., "user.email"). */
  @NotBlank private final String key;

  /** Human-readable description of this variable. */
  @NotBlank private final String label;

  /** The data type of this variable's value. */
  @NotNull private final VariableType type;

  /** Whether this variable holds a single value or multiple values. */
  @NotNull private final ContractCardinality cardinality;

  /** Child variables for hierarchical/nested data structures. */
  private final List<ContractVariable> children;

  private ContractVariable(
      @NotBlank final String key,
      @NotBlank final String label,
      @NotNull final VariableType type,
      @NotNull final ContractCardinality cardinality,
      final List<ContractVariable> children) {
    this.key = key;
    this.label = label;
    this.type = type;
    this.cardinality = cardinality;
    this.children = children;
  }

  /**
   * Creates a contract variable without children.
   *
   * @param key the variable key for template reference
   * @param label the human-readable description
   * @param type the data type of the variable
   * @param cardinality single or multiple value
   * @return a new ContractVariable instance
   */
  public static ContractVariable variable(
      @NotBlank final String key,
      @NotBlank final String label,
      @NotNull final VariableType type,
      @NotNull final ContractCardinality cardinality) {
    return new ContractVariable(key, label, type, cardinality, List.of());
  }

  /**
   * Creates a contract variable with child variables.
   *
   * @param key the variable key for template reference
   * @param label the human-readable description
   * @param type the data type of the variable
   * @param cardinality single or multiple value
   * @param children nested child variables
   * @return a new ContractVariable instance
   */
  public static ContractVariable variable(
      @NotBlank final String key,
      @NotBlank final String label,
      @NotNull final VariableType type,
      @NotNull final ContractCardinality cardinality,
      final List<ContractVariable> children) {
    return new ContractVariable(key, label, type, cardinality, children);
  }
}
