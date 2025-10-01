package io.openaev.rest.payload.regex_group;

import io.openaev.database.model.ContractOutputElement;
import io.openaev.database.model.RegexGroup;
import io.openaev.database.repository.RegexGroupRepository;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Slf4j
public class RegexGroupService {

  private final RegexGroupRepository regexGroupRepository;

  // -- ENTITY --

  /**
   * Copy a set of existing RegexGroup entities and associate the copies with the given
   * ContractOutputElement.
   */
  public void copyRegexGroupsFromEntity(
      Set<RegexGroup> sourceGroups, ContractOutputElement targetElement) {
    if (sourceGroups == null || targetElement == null) {
      return;
    }

    Set<RegexGroup> copiedGroups =
        sourceGroups.stream()
            .filter(Objects::nonNull)
            .map(RegexGroupUtils::copyFromEntity)
            .peek(regexGroup -> regexGroup.setContractOutputElement(targetElement))
            .collect(Collectors.toSet());

    targetElement.setRegexGroups(copiedGroups);
  }

  // -- INPUT --

  /**
   * Create or update RegexGroup entities based on the input DTOs and associate them with the given
   * ContractOutputElement.
   */
  public void copyRegexGroupsFromInput(
      Set<RegexGroupInput> inputs, ContractOutputElement targetElement) {
    if (inputs == null || targetElement == null) {
      return;
    }

    Set<RegexGroup> regexGroups =
        inputs.stream()
            .filter(Objects::nonNull)
            .map(this::mapFromInput)
            .peek(regexGroup -> regexGroup.setContractOutputElement(targetElement))
            .collect(Collectors.toSet());

    targetElement.setRegexGroups(regexGroups);
  }

  private RegexGroup mapFromInput(RegexGroupInput input) {
    if (input.getId() == null) {
      return RegexGroupUtils.copyFromInput(input, new RegexGroup());
    }
    return regexGroupRepository
        .findById(input.getId())
        .map(existing -> RegexGroupUtils.copyFromInput(input, existing))
        .orElseGet(() -> RegexGroupUtils.copyFromInput(input, new RegexGroup()));
  }
}
