package io.openaev.injector_contract.fields;

import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS;
import static io.openaev.injector_contract.ContractCardinality.Multiple;

import io.openaev.model.inject.form.Expectation;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Getter;

/**
 * Contract element representing an expectations configuration field.
 *
 * <p>Expectations fields allow users to define the expected outcomes of an injection, such as
 * detection, prevention, or manual verification requirements.
 *
 * @see ContractCardinalityElement
 * @see Expectation
 */
@Getter
public class ContractExpectations extends ContractCardinalityElement {

  /** Pre-configured expectations to include by default. */
  List<Expectation> predefinedExpectations;

  /**
   * Creates a new expectations field with predefined expectations.
   *
   * @param expectations the default expectations to include
   */
  private ContractExpectations(@NotNull final List<Expectation> expectations) {
    super(CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS, "Expectations", Multiple);
    this.predefinedExpectations = expectations;
  }

  /**
   * Creates an expectations field with no predefined expectations.
   *
   * @return a configured ContractExpectations instance
   */
  public static ContractExpectations expectationsField() {
    return new ContractExpectations(List.of());
  }

  /**
   * Creates an expectations field with predefined expectations.
   *
   * @param expectations the default expectations to include
   * @return a configured ContractExpectations instance
   */
  public static ContractExpectations expectationsField(
      @NotEmpty final List<Expectation> expectations) {
    return new ContractExpectations(expectations);
  }

  @Override
  public ContractFieldType getType() {
    return ContractFieldType.Expectation;
  }
}
