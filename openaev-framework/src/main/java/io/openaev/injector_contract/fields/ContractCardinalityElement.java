package io.openaev.injector_contract.fields;

import io.openaev.injector_contract.ContractCardinality;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Abstract base class for contract elements that support multiple values.
 *
 * <p>Cardinality elements extend the base ContractElement with support for:
 *
 * <ul>
 *   <li>Single value ({@link ContractCardinality#One}) or multiple values ({@link
 *       ContractCardinality#Multiple})
 *   <li>Default value(s) for pre-populating the field
 * </ul>
 *
 * @see ContractElement
 * @see ContractCardinality
 */
@Getter
public abstract class ContractCardinalityElement extends ContractElement {

  /** The cardinality (single or multiple) for this field. */
  private final ContractCardinality cardinality;

  /** Default value(s) to pre-populate the field. */
  @Setter private List<String> defaultValue = new ArrayList<>();

  /**
   * Creates a new cardinality element.
   *
   * @param key the unique identifier for this field
   * @param label the display label
   * @param cardinality single or multiple value support
   */
  public ContractCardinalityElement(String key, String label, ContractCardinality cardinality) {
    super(key, label);
    this.cardinality = cardinality;
  }
}
