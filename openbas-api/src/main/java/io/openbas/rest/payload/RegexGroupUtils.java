package io.openbas.rest.payload;

import static java.time.Instant.now;
import static org.flywaydb.core.internal.util.StringUtils.hasText;

import io.openbas.database.model.ContractOutputElement;
import io.openbas.database.model.RegexGroup;
import io.openbas.database.repository.RegexGroupRepository;
import io.openbas.rest.payload.form.RegexGroupInput;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class RegexGroupUtils {

  private final RegexGroupRepository regexGroupRepository;

  public void copyRegexGroups(
      Set<?> inputElements, ContractOutputElement contractOutputElement, boolean copyId) {
    if (inputElements == null || contractOutputElement == null) {
      return;
    }

    Instant now = now();

    Set<RegexGroup> regexGroups =
        inputElements.stream()
            .map(inputElement -> copyRegexGroup(inputElement, contractOutputElement, copyId, now))
            .collect(Collectors.toSet());

    contractOutputElement.setRegexGroups(regexGroups);
  }

  private RegexGroup copyRegexGroup(
      Object inputElement,
      ContractOutputElement contractOutputElement,
      boolean copyId,
      Instant now) {
    RegexGroup regexGroup;
    if (copyId && hasText(((RegexGroupInput) inputElement).getId())) {
      regexGroup =
          this.regexGroupRepository
              .findById(((RegexGroupInput) inputElement).getId())
              .orElseThrow();
    } else {
      regexGroup = new RegexGroup();
    }
    regexGroup.setContractOutputElement(contractOutputElement);
    regexGroup.setCreatedAt(now);
    regexGroup.setUpdatedAt(now);

    if (inputElement instanceof RegexGroupInput) {
      copyFromInput((RegexGroupInput) inputElement, regexGroup);
    } else if (inputElement instanceof RegexGroup) {
      copyFromEntity((RegexGroup) inputElement, regexGroup);
    }

    return regexGroup;
  }

  private void copyFromInput(RegexGroupInput input, RegexGroup regexGroup) {
    regexGroup.setField(input.getField());
    regexGroup.setIndexValues(input.getIndexValues());
  }

  private void copyFromEntity(RegexGroup input, RegexGroup regexGroup) {
    regexGroup.setField(input.getField());
    regexGroup.setIndexValues(input.getIndexValues());
  }
}
