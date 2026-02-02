package io.openaev.injector_contract.outputs;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.ContractOutputType;
import lombok.Data;

/**
 * Represents an output element defined in an injector contract.
 *
 * <p>Output elements describe the data produced by an injection execution. This metadata is used
 * to:
 *
 * <ul>
 *   <li>Define the structure of execution results
 *   <li>Enable proper display of output data in the UI
 *   <li>Support security finding generation from outputs
 *   <li>Handle multi-value outputs
 * </ul>
 *
 * @see ContractOutputType
 */
@Data
public class InjectorContractContentOutputElement {

  /** The type of output (e.g., text, number, file). */
  @JsonProperty("type")
  private ContractOutputType type;

  /** The field name/key for this output. */
  @JsonProperty("field")
  private String field;

  /** Display labels for this output in different contexts. */
  @JsonProperty("labels")
  private String[] labels;

  /** Whether this output can contain multiple values. */
  @JsonProperty("isMultiple")
  private boolean multiple;

  /** Whether this output can be used as a security finding. */
  @JsonProperty("isFindingCompatible")
  private boolean findingCompatible;
}
