package io.openbas.rest.payload;

import static io.openbas.helper.StreamHelper.iterableToSet;
import static io.openbas.utils.StringUtils.isValidRegex;
import static java.time.Instant.now;
import static org.flywaydb.core.internal.util.StringUtils.hasText;

import io.openbas.database.model.ContractOutputElement;
import io.openbas.database.model.OutputParser;
import io.openbas.database.repository.ContractOutputElementRepository;
import io.openbas.database.repository.TagRepository;
import io.openbas.rest.exception.BadRequestException;
import io.openbas.rest.payload.form.ContractOutputElementInput;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class ContractOutputElementUtils {

  private final TagRepository tagRepository;
  private final RegexGroupUtils regexGroupUtils;
  private final ContractOutputElementRepository contractOutputElementRepository;

  public void copyContractOutputElements(
      Set<?> inputElements, OutputParser outputParser, boolean copyId) {
    if (inputElements == null || outputParser == null) {
      return;
    }
    Instant now = now();
    Set<ContractOutputElement> contractOutputElements =
        inputElements.stream()
            .map(inputElement -> copyContractOutputElement(inputElement, outputParser, copyId, now))
            .collect(Collectors.toSet());

    outputParser.setContractOutputElements(contractOutputElements);
  }

  private ContractOutputElement copyContractOutputElement(
      Object inputElement, OutputParser outputParser, boolean copyId, Instant now) {
    ContractOutputElement contractOutputElement;
    if (copyId && hasText(((ContractOutputElementInput) inputElement).getId())) {
      contractOutputElement =
          this.contractOutputElementRepository
              .findById(((ContractOutputElementInput) inputElement).getId())
              .orElseThrow();
    } else {
      contractOutputElement = new ContractOutputElement();
    }
    contractOutputElement.setOutputParser(outputParser);
    contractOutputElement.setCreatedAt(now);
    contractOutputElement.setUpdatedAt(now);

    if (inputElement instanceof ContractOutputElementInput) {
      copyFromInput((ContractOutputElementInput) inputElement, contractOutputElement, copyId);
    } else if (inputElement instanceof ContractOutputElement) {
      copyFromEntity((ContractOutputElement) inputElement, contractOutputElement, copyId);
    }
    return contractOutputElement;
  }

  private void copyFromInput(
      ContractOutputElementInput input,
      ContractOutputElement contractOutputElement,
      boolean copyId) {

    if (!isValidRegex(input.getRule())) {
      throw new BadRequestException(
          String.format("Invalid rule: %s with regex: %s", input.getName(), input.getRule()));
    }

    BeanUtils.copyProperties(input, contractOutputElement, "id", "tags", "regexGroups");
    if (tagRepository != null) {
      contractOutputElement.setTags(iterableToSet(tagRepository.findAllById(input.getTagIds())));
    }
    regexGroupUtils.copyRegexGroups(input.getRegexGroups(), contractOutputElement, copyId);
  }

  private void copyFromEntity(
      ContractOutputElement existing, ContractOutputElement contractOutputElement, boolean copyId) {

    if (!isValidRegex(existing.getRule())) {
      throw new BadRequestException(
          String.format("Invalid rule: %s with regex: %s", existing.getName(), existing.getRule()));
    }

    BeanUtils.copyProperties(existing, contractOutputElement, "id", "tags", "regexGroups");
    contractOutputElement.setTags(new HashSet<>(existing.getTags()));
    regexGroupUtils.copyRegexGroups(existing.getRegexGroups(), contractOutputElement, copyId);
  }
}
