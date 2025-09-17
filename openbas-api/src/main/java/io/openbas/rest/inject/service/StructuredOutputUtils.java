package io.openbas.rest.inject.service;

import static io.openbas.utils.ExecutionTraceUtils.convertExecutionAction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import io.openbas.database.model.*;
import io.openbas.rest.inject.form.InjectExecutionInput;
import jakarta.annotation.Resource;
import java.util.*;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.stereotype.Component;

@Log
@RequiredArgsConstructor
@Component
public class StructuredOutputUtils {

  @Resource private final ObjectMapper mapper;

  Set<OutputParser> extractOutputParsers(Inject inject) {
    Optional<Payload> optionalPayload = inject.getPayload();
    if (optionalPayload.isEmpty()) {
      log.info("No payload found for inject: " + inject.getId());
      return Collections.emptySet();
    }

    Set<OutputParser> outputParsers = optionalPayload.get().getOutputParsers();
    if (outputParsers == null || outputParsers.isEmpty()) {
      log.info("No output parsers available for payload used in inject: " + inject.getId());
      return Collections.emptySet();
    }
    return outputParsers;
  }

  /**
   * Computes the structured output from the injection execution input.
   *
   * <p>Initially, it verifies if the structured output is already available. If it is not, and the
   * input pertains to an execution action, the method attempts to generate the structured output
   * from the raw execution output using the output parsers defined in the payload used for the
   * injection.
   */
  public Optional<ObjectNode> computeStructuredOutput(
      Set<OutputParser> outputParsers, InjectExecutionInput input) throws JsonProcessingException {
    // Return pre-computed structured output if available
    if (input.getOutputStructured() != null) {
      return Optional.ofNullable(mapper.readValue(input.getOutputStructured(), ObjectNode.class));
    }

    // Only compute if the action is actual execution
    if (ExecutionTraceAction.EXECUTION.equals(convertExecutionAction(input.getAction()))) {
      return computeStructuredOutputFromOutputParsers(outputParsers, input.getMessage());
    }

    return Optional.empty();
  }

  public Optional<ObjectNode> computeStructuredOutputFromOutputParsers(
      Set<OutputParser> outputParsers, String rawOutput) {
    ObjectNode result = mapper.createObjectNode();

    if (outputParsers == null) {
      return Optional.empty();
    }

    for (OutputParser outputParser : outputParsers) {
      String rawOutputByMode = extractRawOutputByMode(rawOutput, outputParser.getMode());
      if (rawOutputByMode == null) {
        continue;
      }

      Optional<ObjectNode> parsed;
      switch (outputParser.getType()) {
        case REGEX:
        default:
          parsed =
              computeStructuredOutputUsingRegexRules(
                  rawOutputByMode, outputParser.getContractOutputElements());
          break;
      }

      parsed.ifPresent(result::setAll);
    }

    return result.isEmpty() ? Optional.empty() : Optional.of(result);
  }

  private static String extractRawOutputByMode(String rawOutput, ParserMode mode) {
    if (rawOutput == null || rawOutput.isEmpty()) {
      return "";
    }

    try {
      ObjectMapper objectMapper = new ObjectMapper();
      JsonNode rootNode = objectMapper.readTree(rawOutput);

      if (mode == ParserMode.STDOUT && rootNode.has("stdout")) {
        return rootNode.get("stdout").asText();
      } else if (mode == ParserMode.STDERR && rootNode.has("stderr")) {
        return rootNode.get("stderr").asText();
      }
    } catch (Exception e) {
      log.log(Level.WARNING, e.getMessage(), e);
    }

    return "";
  }

  /**
   * Builds structured output from raw output using regex rules defined in contract output elements.
   *
   * <p>Each rule is applied to the raw output, and matched data is validated and added to the
   * resulting JSON structure.
   */
  public Optional<ObjectNode> computeStructuredOutputUsingRegexRules(
      String rawOutputByMode, Set<ContractOutputElement> contractOutputElements) {
    Map<String, Pattern> patternCache = new HashMap<>();
    ObjectNode resultRoot = mapper.createObjectNode();

    for (ContractOutputElement contractOutputElement : contractOutputElements) {
      String regex = contractOutputElement.getRule();
      // Compile and cache regex
      Pattern pattern =
          patternCache.computeIfAbsent(
              regex,
              r -> {
                try {
                  return Pattern.compile(
                      r,
                      Pattern.MULTILINE
                          | Pattern.CASE_INSENSITIVE
                          | Pattern.UNICODE_CHARACTER_CLASS);
                } catch (PatternSyntaxException e) {
                  log.log(Level.INFO, "Invalid regex pattern: " + r, e.getMessage());
                  return null;
                }
              });

      if (pattern == null) {
        continue;
      }
      String ansiPattern = "\\u001b\\[[0-9;]*m";
      String cleanOutput = rawOutputByMode.replaceAll(ansiPattern, "");

      Matcher matcher = pattern.matcher(cleanOutput);
      ArrayNode matchesArray = mapper.createArrayNode();

      while (matcher.find()) {
        buildStructuredJsonNode(contractOutputElement, matcher)
            .filter(structured -> contractOutputElement.getType().validate.apply(structured))
            .ifPresent(matchesArray::add);
      }
      resultRoot.set(contractOutputElement.getKey(), matchesArray);
    }

    return Optional.of(resultRoot);
  }

  public Optional<JsonNode> buildStructuredJsonNode(
      ContractOutputElement element, Matcher matcher) {
    ContractOutputType type = element.getType();

    // Case: primitive types like Text, Number, IPv4, IPv6
    if (type.fields == null || type.technicalType != ContractOutputTechnicalType.Object) {
      String extracted = extractValues(element.getRegexGroups(), matcher);

      if (extracted == null) {
        return Optional.empty();
      }

      return type.technicalType == ContractOutputTechnicalType.Number
          ? Optional.of(toNumericValue(extracted))
          : Optional.of(mapper.valueToTree(extracted));
    }

    // Case: complex types like portscan, credentials, CVE
    ObjectNode objectNode = mapper.createObjectNode();

    for (ContractOutputField field : type.fields) {
      Set<RegexGroup> matchingGroups =
          element.getRegexGroups().stream()
              .filter(rg -> rg.getField().equals(field.getKey()))
              .collect(Collectors.toSet());

      String concatedValues = extractValues(matchingGroups, matcher);

      JsonNode valueNode =
          (field.getType() == ContractOutputTechnicalType.Number)
              ? toNumericValue(concatedValues)
              : mapper.valueToTree(concatedValues);

      objectNode.set(field.getKey(), valueNode);
    }

    return Optional.of(objectNode);
  }

  private static ValueNode toNumericValue(String extracted) {
    try {
      return new IntNode(Integer.parseInt(extracted));
    } catch (NumberFormatException e) {
      log.warning("Invalid number format: " + extracted);
      return NullNode.getInstance();
    }
  }

  private static String extractValues(Set<RegexGroup> regexGroups, Matcher matcher) {
    for (RegexGroup regexGroup : regexGroups) {
      String[] indexes =
          Arrays.stream(regexGroup.getIndexValues().split("\\$", -1))
              .filter(index -> !index.isEmpty())
              .toArray(String[]::new);

      StringBuilder concatenated = new StringBuilder();

      for (String index : indexes) {
        try {
          int groupIndex = Integer.parseInt(index);
          if (groupIndex > matcher.groupCount()) {
            log.warning("Skipping invalid group index: " + groupIndex);
            continue;
          }

          String extracted = matcher.group(groupIndex);
          if (extracted != null && !extracted.isEmpty()) {
            concatenated.append(extracted.trim());
          }

        } catch (NumberFormatException | IllegalStateException e) {
          log.log(Level.SEVERE, "Invalid regex group index: " + index, e.getMessage());
        }
      }

      // If something was extracted and concatenated, return it
      if (!concatenated.isEmpty()) {
        return concatenated.toString();
      }
    }

    return null;
  }
}
