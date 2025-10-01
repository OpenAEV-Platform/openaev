package io.openaev.rest.payload.contract_output_element;

import static io.openaev.rest.payload.contract_output_element.ContractOutputElementUtils.copyFromEntity;

import io.openaev.database.model.ContractOutputElement;
import io.openaev.database.model.OutputParser;
import io.openaev.database.repository.ContractOutputElementRepository;
import io.openaev.rest.payload.regex_group.RegexGroupService;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Slf4j
public class ContractOutputElementService {

  private final ContractOutputElementRepository contractOutputElementRepository;
  private final RegexGroupService regexGroupService;

  // -- ENTITY --

  /**
   * Copies a set of {@link ContractOutputElement} entities into a new set attached to the given
   * {@link OutputParser}.
   *
   * <p>This performs a deep copy of each element, including its regex groups.
   */
  public void copyContractOutputElementsFromEntity(
      Set<ContractOutputElement> source, OutputParser targetElement) {
    if (source == null || targetElement == null) {
      return;
    }
    Set<ContractOutputElement> contractOutputElements =
        source.stream()
            .filter(Objects::nonNull)
            .map(this::copyContractOutputElement)
            .peek(contractOutputElement -> contractOutputElement.setOutputParser(targetElement))
            .collect(Collectors.toSet());

    targetElement.setContractOutputElements(contractOutputElements);
  }

  /** Creates a deep copy of a single {@link ContractOutputElement} including its regex groups. */
  private ContractOutputElement copyContractOutputElement(ContractOutputElement source) {
    ContractOutputElement copy = copyFromEntity(source);
    regexGroupService.copyRegexGroupsFromEntity(source.getRegexGroups(), copy);
    return copy;
  }

  // -- INPUT --

  /**
   * Converts a set of {@link ContractOutputElementInput} into a set of {@link
   * ContractOutputElement} and attaches them to the given {@link OutputParser}.
   *
   * <p>For each input element:
   *
   * <ul>
   *   <li>If {@code id} is null, a new entity is created.
   *   <li>If {@code id} exists, it is fetched from the repository and updated.
   * </ul>
   */
  public void copyContractOutputElementsFromInput(
      Set<ContractOutputElementInput> source, OutputParser targetElement) {
    if (source == null || targetElement == null) {
      return;
    }
    Set<ContractOutputElement> contractOutputElements =
        source.stream()
            .filter(Objects::nonNull)
            .map(this::mapFromInput)
            .peek(contractOutputElement -> contractOutputElement.setOutputParser(targetElement))
            .collect(Collectors.toSet());

    targetElement.setContractOutputElements(contractOutputElements);
  }

  /**
   * Converts a single {@link ContractOutputElementInput} into a {@link ContractOutputElement}.
   *
   * <p>If the input has an ID, fetches the corresponding entity from the repository and updates it.
   * If not, creates a new instance. Regex groups are copied accordingly.
   */
  private ContractOutputElement mapFromInput(ContractOutputElementInput input) {
    if (input.getId() == null) {
      ContractOutputElement newElement =
          ContractOutputElementUtils.copyFromInput(input, new ContractOutputElement());
      regexGroupService.copyRegexGroupsFromInput(input.getRegexGroups(), newElement);
      return newElement;
    }
    return contractOutputElementRepository
        .findById(input.getId())
        .map(
            source -> {
              ContractOutputElement copy = ContractOutputElementUtils.copyFromInput(input, source);
              regexGroupService.copyRegexGroupsFromInput(input.getRegexGroups(), copy);
              return copy;
            })
        .orElseGet(
            () -> {
              ContractOutputElement copy =
                  ContractOutputElementUtils.copyFromInput(input, new ContractOutputElement());
              regexGroupService.copyRegexGroupsFromInput(input.getRegexGroups(), copy);
              return copy;
            });
  }
}
