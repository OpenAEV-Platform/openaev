package io.openaev.rest.payload.contract_output_element;

import static io.openaev.utils.StringUtils.isValidRegex;

import io.openaev.database.model.ContractOutputElement;
import io.openaev.rest.exception.BadRequestException;
import jakarta.validation.constraints.NotBlank;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeanUtils;

public class ContractOutputElementUtils {

  private ContractOutputElementUtils() {}

  /** Creates a copy of the given {@link ContractOutputElement} entity. */
  public static ContractOutputElement copyFromEntity(@NotNull final ContractOutputElement source) {
    validateRule(source.getName(), source.getRule());

    ContractOutputElement copy = new ContractOutputElement();
    BeanUtils.copyProperties(source, copy, "id", "tags", "regexGroups");
    return copy;
  }

  /**
   * Copies properties from the given {@link ContractOutputElementInput} into the target {@link
   * ContractOutputElement}.
   */
  public static ContractOutputElement copyFromInput(
      @NotNull final ContractOutputElementInput input,
      @NotNull final ContractOutputElement target) {
    validateRule(input.getName(), input.getRule());

    BeanUtils.copyProperties(input, target, "id", "tags", "regexGroups");
    return target;
  }

  private static void validateRule(@NotBlank final String ruleName, @NotBlank final String rule) {
    if (!isValidRegex(rule)) {
      throw new BadRequestException(
          String.format("Invalid rule: %s with regex: %s", ruleName, rule));
    }
  }
}
