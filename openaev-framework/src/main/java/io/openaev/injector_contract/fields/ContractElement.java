package io.openaev.injector_contract.fields;

import io.openaev.model.LinkedFieldModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * Abstract base class for all contract form elements.
 *
 * <p>Contract elements define the input fields that appear in an injection form. Each element has:
 *
 * <ul>
 *   <li>A unique key for identifying the field and its value
 *   <li>A display label shown to users
 *   <li>Validation rules (mandatory, conditions)
 *   <li>Visibility conditions
 *   <li>Links to other fields for dynamic behavior
 * </ul>
 *
 * <p>Subclasses provide specific field types:
 *
 * <ul>
 *   <li>{@link ContractText} - Single-line text input
 *   <li>{@link ContractTextArea} - Multi-line text/rich text input
 *   <li>{@link ContractCheckbox} - Boolean checkbox
 *   <li>{@link ContractNumber} - Numeric input
 *   <li>{@link ContractSelect} - Dropdown selection
 *   <li>{@link ContractAsset}, {@link ContractAssetGroup} - Asset selection
 *   <li>{@link ContractTeam} - Team selection
 *   <li>{@link ContractExpectations} - Expectation configuration
 * </ul>
 *
 * @see ContractDef for building contract field definitions
 */
@Data
public abstract class ContractElement {

  /** Unique identifier for this field within the contract. */
  private String key;

  /** Display label shown to users. */
  private String label;

  /** Whether this field must be filled (default: true). */
  private boolean mandatory = true;

  /** Whether this field is read-only (default: false). */
  private boolean readOnly = false;

  /**
   * Keys of fields in a mutual mandatory group.
   *
   * <p>When set, at least one field in the group must be filled.
   */
  private List<String> mandatoryGroups;

  /**
   * Keys of fields that, when filled, make this field mandatory.
   *
   * <p>Used for conditional validation based on other field values.
   */
  private List<String> mandatoryConditionFields;

  /**
   * Map of field keys to values that trigger mandatory validation.
   *
   * <p>Values can be String or List&lt;String&gt; for multiple value matching.
   */
  private Map<String, Object> mandatoryConditionValues;

  /** Keys of fields whose values control this field's visibility. */
  private List<String> visibleConditionFields;

  /** Map of field keys to values that make this field visible. */
  private Map<String, String> visibleConditionValues;

  /**
   * Fields that this element is linked to for dynamic behavior.
   *
   * <p>For example, a targeted property field linked to a targeted asset field enables selection of
   * properties (IP, hostname) from the selected asset.
   */
  private List<LinkedFieldModel> linkedFields = new ArrayList<>();

  /** Pre-selected values from linked fields. */
  private List<String> linkedValues = new ArrayList<>();

  /**
   * Creates a new contract element.
   *
   * @param key the unique identifier for this field
   * @param label the display label
   */
  protected ContractElement(String key, String label) {
    this.key = key;
    this.label = label;
  }

  /**
   * Sets the linked fields by converting contract elements to link models.
   *
   * @param linkedFields the contract elements to link to
   */
  public void setLinkedFields(List<ContractElement> linkedFields) {
    if (linkedFields == null) {
      this.linkedFields = new ArrayList<>();
    } else {
      this.linkedFields = linkedFields.stream().map(LinkedFieldModel::fromField).toList();
    }
  }

  /**
   * Returns the type of this contract element.
   *
   * @return the field type
   */
  public abstract ContractFieldType getType();
}
