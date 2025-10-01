package io.openaev.rest.payload.output_parser;

import io.openaev.database.model.OutputParser;
import io.openaev.database.model.Payload;
import io.openaev.database.repository.OutputParserRepository;
import io.openaev.rest.payload.contract_output_element.ContractOutputElementService;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Slf4j
public class OutputParserService {

  private final OutputParserRepository outputParserRepository;
  private final ContractOutputElementService contractOutputElementService;

  // -- ENTITY --

  /**
   * Copies a set of {@link OutputParser} entities and attaches them to the given {@link Payload}.
   *
   * <p>Each {@link OutputParser} is deeply copied along with its {@link
   * io.openaev.database.model.ContractOutputElement}s.
   */
  public void copyOutputParsersFromEntity(Set<OutputParser> source, Payload targetElement) {
    if (source == null) {
      return;
    }
    Set<OutputParser> outputParsers =
        source.stream()
            .filter(Objects::nonNull)
            .map(this::copyOutputParser)
            .peek(parser -> parser.setPayload(targetElement))
            .collect(Collectors.toSet());

    targetElement.setOutputParsers(outputParsers);
  }

  /**
   * Deep copies a single {@link OutputParser}, including its {@link
   * io.openaev.database.model.ContractOutputElement}s.
   */
  private OutputParser copyOutputParser(OutputParser source) {
    OutputParser copy = OutputParserUtils.copyFromEntity(source);
    contractOutputElementService.copyContractOutputElementsFromEntity(
        source.getContractOutputElements(), copy);
    return copy;
  }

  // -- INPUT --

  /**
   * Converts a set of {@link OutputParserInput} into {@link OutputParser} entities and attaches
   * them to the given {@link Payload}.
   *
   * <p>For each input:
   *
   * <ul>
   *   <li>If {@code id} is null, a new parser is created.
   *   <li>If {@code id} is set, the parser is retrieved from the repository and updated.
   * </ul>
   */
  public void copyOutputParsersFromInput(Set<OutputParserInput> source, Payload targetElement) {
    if (source == null) {
      return;
    }
    Set<OutputParser> outputParsers =
        source.stream()
            .filter(Objects::nonNull)
            .map(this::mapFromInput)
            .peek(parser -> parser.setPayload(targetElement))
            .collect(Collectors.toSet());

    targetElement.setOutputParsers(outputParsers);
  }

  /**
   * Converts a single {@link OutputParserInput} into an {@link OutputParser} entity.
   *
   * <p>If the input has an ID, the corresponding entity is retrieved and updated. Otherwise, a new
   * instance is created. Contract output elements are copied accordingly.
   */
  private OutputParser mapFromInput(OutputParserInput input) {
    if (input.getId() == null) {
      OutputParser newParser = OutputParserUtils.copyFromInput(input, new OutputParser());
      contractOutputElementService.copyContractOutputElementsFromInput(
          input.getContractOutputElements(), newParser);
      return newParser;
    }

    return outputParserRepository
        .findById(input.getId())
        .map(
            source -> {
              OutputParser copy = OutputParserUtils.copyFromInput(input, source);
              contractOutputElementService.copyContractOutputElementsFromInput(
                  input.getContractOutputElements(), copy);
              return copy;
            })
        .orElseGet(
            () -> {
              OutputParser copy = OutputParserUtils.copyFromInput(input, new OutputParser());
              contractOutputElementService.copyContractOutputElementsFromInput(
                  input.getContractOutputElements(), copy);
              return copy;
            });
  }
}
