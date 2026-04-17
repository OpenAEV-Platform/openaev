package io.openaev.injector_contract.fields;

import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS;
import static io.openaev.injector_contract.ContractCardinality.Multiple;

import io.openaev.model.inject.form.Expectation;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
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

  List<Expectation> availableExpectations;

  /**
   * Creates a new expectations field with predefined expectations.
   *
   * @param expectations the default expectations to include
   */
  private ContractExpectations(@NotNull final List<Expectation> expectations) {
    super(CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS, "Expectations", Multiple);
    this.predefinedExpectations = expectations;
    this.availableExpectations = expectations;
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

  /**
   * Creates an expectations field with distinct predefined and available expectations.
   *
   * <p>Use this factory when the set of expectations selectable by the user (available) is broader
   * than the ones pre-populated by default (predefined), e.g. for payload-based contracts.
   *
   * @param predefined expectations pre-populated in the form
   * @param available full list of expectations the user may choose from
   * @return a configured ContractExpectations instance
   */
  public static ContractExpectations expectationsField(
      final List<Expectation> predefined, final List<Expectation> available) {
    ContractExpectations field = new ContractExpectations(predefined);
    field.availableExpectations = new ArrayList<>(available);
    return field;
  }

  @Override
  public ContractFieldType getType() {
    return ContractFieldType.Expectation;
  }
}
