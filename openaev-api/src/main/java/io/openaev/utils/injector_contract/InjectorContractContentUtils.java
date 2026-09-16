package io.openaev.utils.injector_contract;

import static io.openaev.database.model.InjectorContract.*;
import static io.openaev.utils.mapper.InjectExpectationMapper.NODE_EXPECTATION_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.*;
import io.openaev.injector_contract.fields.ContractFieldType;
import io.openaev.injector_contract.outputs.InjectorContractContentOutputElement;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class InjectorContractContentUtils {

  private final ObjectMapper mapper;

  public static final String OUTPUTS = "outputs";
  public static final String FIELDS = "fields";
  public static final String MULTIPLE = "n";

  /** JSON property carrying the expected security platform types of a predefined expectation. */
  public static final String NODE_EXPECTED_SECURITY_PLATFORM_TYPES =
      "expectation_expected_security_platform_types";

  /**
   * Retrieves all contract output elements from the injector contract.
   *
   * @param injectorContract the injector contract to inspect
   * @return list of contract output elements
   */
  public List<InjectorContractContentOutputElement> getAllContractOutputs(
      InjectorContract injectorContract) {
    return this.getContractOutputs(injectorContract.getConvertedContent(), mapper).stream()
        .toList();
  }

  /**
   * Retrieves all contract output elements from the output parsers.
   *
   * @param outputParsers the set of output parsers to inspect
   * @return list of contract output elements
   */
  public List<ContractOutputElement> getAllContractOutputs(Set<OutputParser> outputParsers) {
    return getAllContractOutputs(outputParsers, true);
  }

  /**
   * Retrieves contract output elements from output parsers, optionally keeping only finding
   * outputs.
   *
   * @param outputParsers the set of output parsers to inspect
   * @param onlyFindings whether to keep only outputs flagged as findings
   * @return list of contract output elements
   */
  public List<ContractOutputElement> getAllContractOutputs(
      Set<OutputParser> outputParsers, boolean onlyFindings) {
    return outputParsers.stream()
        .flatMap(outputParser -> outputParser.getContractOutputElements().stream())
        .filter(output -> !onlyFindings || output.isFinding())
        .toList();
  }

  /**
   * Function used to get the outputs from the injector contract content.
   *
   * @param content Injector Contract content
   * @param mapper ObjectMapper used to convert JSON to Java objects
   * @return List of ContractOutputElement ( from Injector contract content )
   */
  public List<InjectorContractContentOutputElement> getContractOutputs(
      @NotNull final ObjectNode content, ObjectMapper mapper) {
    return StreamSupport.stream(content.get(OUTPUTS).spliterator(), false)
        .map(
            jsonNode -> {
              try {
                return mapper.treeToValue(jsonNode, InjectorContractContentOutputElement.class);
              } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Error processing JSON: " + jsonNode, e);
              }
            })
        .toList();
  }

  /**
   * Function used to get the dynamic fields for inject from the injector contract.
   *
   * @param injectorContract InjectorContract object containing the converted content
   * @return ObjectNode containing the dynamic fields for inject
   */
  public ObjectNode getDynamicInjectorContractFieldsForInject(InjectorContract injectorContract) {
    ObjectNode convertedContent = injectorContract.getConvertedContent();

    if (convertedContent == null) {
      return null;
    }

    if (convertedContent.has(FIELDS) && convertedContent.get(FIELDS).isArray()) {
      ArrayNode fieldsArray = (ArrayNode) convertedContent.get(FIELDS);
      ArrayNode fieldsNode = fieldsArray.deepCopy();
      ObjectNode injectContent = mapper.createObjectNode();

      for (JsonNode field : fieldsNode) {
        String key = field.get(CONTRACT_ELEMENT_CONTENT_KEY).asText();

        if (CONTRACT_ELEMENT_CONTENT_KEY_NOT_DYNAMIC.contains(key)) {
          continue;
        }

        JsonNode valueNode;

        // For expectation field, we should use availableExpectations filtered by isPredefined
        if (CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS.equals(key)) {
          JsonNode available = field.get(AVAILABLE_EXPECTATIONS);
          if (available != null && available.isArray()) {
            ArrayNode predefined = mapper.createArrayNode();
            for (JsonNode exp : available) {
              if (exp.has(IS_PREDEFINED_EXPECTATION)
                  && exp.get(IS_PREDEFINED_EXPECTATION).asBoolean()) {
                predefined.add(exp);
              }
            }
            valueNode = predefined.isEmpty() ? field.get(DEFAULT_VALUE_FIELD) : predefined;
          } else {
            valueNode = field.get(DEFAULT_VALUE_FIELD);
          }
        } else {
          valueNode = field.get(DEFAULT_VALUE_FIELD);
        }

        if (valueNode == null || valueNode.isNull() || valueNode.isEmpty()) {
          continue;
        }

        JsonNode cardinalityValueNode = field.get(CONTRACT_ELEMENT_CONTENT_CARDINALITY);
        if (cardinalityValueNode != null
            && !cardinalityValueNode.isNull()
            && !cardinalityValueNode.asText().isEmpty()) {
          String cardinality = cardinalityValueNode.asText();
          if (MULTIPLE.equals(cardinality)) {
            injectContent.set(key, valueNode);
          } else if (valueNode.has(0)) {
            injectContent.set(key, valueNode.get(0));
          }
        } else {
          injectContent.set(key, valueNode);
        }
      }

      return injectContent;
    }

    return null;
  }

  /**
   * Extracts the predefined expectation JSON nodes from the injector contract content.
   *
   * @param injectorContract the injector contract to inspect
   * @return list of predefined expectation JSON nodes (never null)
   */
  public List<JsonNode> getPredefinedExpectationNodes(InjectorContract injectorContract) {
    ObjectNode convertedContent = injectorContract.getConvertedContent();
    if (convertedContent == null
        || !convertedContent.has(FIELDS)
        || !convertedContent.get(FIELDS).isArray()) {
      return List.of();
    }

    for (JsonNode field : convertedContent.get(FIELDS)) {
      String key = field.get(CONTRACT_ELEMENT_CONTENT_KEY).asText();
      if (CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS.equals(key)) {
        JsonNode available = field.get(AVAILABLE_EXPECTATIONS);
        if (available != null && available.isArray()) {
          return StreamSupport.stream(available.spliterator(), false)
              .filter(
                  exp ->
                      exp.has(IS_PREDEFINED_EXPECTATION)
                          && exp.get(IS_PREDEFINED_EXPECTATION).asBoolean())
              .toList();
        }
      }
    }
    return List.of();
  }

  public BaseInjectExpectation.EXPECTATION_TYPE[] getPredefinedExpectations(
      InjectorContract injectorContract) {
    return getPredefinedExpectationNodes(injectorContract).stream()
        .map(node -> node.get(NODE_EXPECTATION_TYPE).asText())
        .map(BaseInjectExpectation.EXPECTATION_TYPE::valueOf)
        .toArray(BaseInjectExpectation.EXPECTATION_TYPE[]::new);
  }

  /**
   * Extracts, for each predefined expectation of the contract, the security platform types expected
   * to fulfil it (e.g. {@code {"DETECTION": ["EDR","SIEM"]}}). Expectations without an explicit
   * list are omitted, meaning "any security platform".
   *
   * @param injectorContract the injector contract to inspect
   * @return map of expectation type to expected security platform types (never null)
   */
  public Map<BaseInjectExpectation.EXPECTATION_TYPE, List<SecurityPlatform.SECURITY_PLATFORM_TYPE>>
      getPredefinedExpectedSecurityPlatforms(InjectorContract injectorContract) {
    Map<BaseInjectExpectation.EXPECTATION_TYPE, List<SecurityPlatform.SECURITY_PLATFORM_TYPE>>
        result = new EnumMap<>(BaseInjectExpectation.EXPECTATION_TYPE.class);

    for (JsonNode expectation : getPredefinedExpectationNodes(injectorContract)) {
      JsonNode typeNode = expectation.get(NODE_EXPECTATION_TYPE);
      JsonNode platformsNode = expectation.get(NODE_EXPECTED_SECURITY_PLATFORM_TYPES);
      if (typeNode == null
          || !typeNode.isTextual()
          || platformsNode == null
          || !platformsNode.isArray()
          || platformsNode.isEmpty()) {
        continue;
      }
      try {
        BaseInjectExpectation.EXPECTATION_TYPE type =
            BaseInjectExpectation.EXPECTATION_TYPE.valueOf(typeNode.asText());
        List<SecurityPlatform.SECURITY_PLATFORM_TYPE> platforms = new ArrayList<>();
        for (JsonNode platform : platformsNode) {
          platforms.add(SecurityPlatform.SECURITY_PLATFORM_TYPE.valueOf(platform.asText()));
        }
        result.put(type, platforms);
      } catch (IllegalArgumentException e) {
        log.warn(
            "Ignoring predefined expectation with unknown type or security platform type: {}",
            expectation,
            e);
      }
    }
    return result;
  }

  /**
   * Function to find if into the injector contract content a field with a key value exist
   *
   * @param injectorContract to analyse
   * @param field to find
   * @return true if field is found, false if not
   */
  public boolean hasField(InjectorContract injectorContract, String field) {
    if (injectorContract == null || injectorContract.getContent() == null) {
      return false;
    }

    try {
      ObjectNode objectNode = (ObjectNode) mapper.readTree(injectorContract.getContent());

      return objectNode.get("fields") != null
          && objectNode.get("fields").isArray()
          && StreamSupport.stream(
                  Spliterators.spliteratorUnknownSize(objectNode.get("fields").iterator(), 0),
                  false)
              .anyMatch(node -> node.has("key") && field.equals(node.get("key").asText()));
    } catch (JsonProcessingException e) {
      return false;
    }
  }

  /**
   * Ensures the inject content has expectations populated.
   *
   * <p>If the content does not already contain expectations, this method reads the injector
   * contract's predefined expectations and injects them into the content with a default score of
   * 100. If expectations are already present, the content is returned unchanged.
   *
   * @param injectorContract the contract defining the available predefined expectations
   * @param finalContent the inject content node to populate; may be {@code null}
   * @return the updated content node with expectations set, or the original if already populated
   */
  public ObjectNode setExpectations(InjectorContract injectorContract, ObjectNode finalContent) {
    if (finalContent == null
        || finalContent.get(CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS) == null
        || finalContent.get(CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS).isEmpty()) {
      try {
        JsonNode jsonNode = mapper.readTree(injectorContract.getContent());
        List<JsonNode> contractElements =
            StreamSupport.stream(jsonNode.get("fields").spliterator(), false)
                .filter(
                    contractElement ->
                        contractElement
                            .get("type")
                            .asText()
                            .equals(ContractFieldType.Expectation.name().toLowerCase()))
                .toList();
        if (!contractElements.isEmpty()) {
          JsonNode contractElement = contractElements.getFirst();
          JsonNode availableNode = contractElement.get(AVAILABLE_EXPECTATIONS);
          if (availableNode != null && !availableNode.isNull() && !availableNode.isEmpty()) {
            finalContent = finalContent != null ? finalContent : mapper.createObjectNode();
            ArrayNode predefinedExpectations = mapper.createArrayNode();
            StreamSupport.stream(availableNode.spliterator(), false)
                .filter(
                    exp ->
                        exp.has(IS_PREDEFINED_EXPECTATION)
                            && exp.get(IS_PREDEFINED_EXPECTATION).asBoolean())
                .forEach(
                    predefinedExpectation -> {
                      ObjectNode newExpectation = predefinedExpectation.deepCopy();
                      newExpectation.put("expectation_score", 100);
                      predefinedExpectations.add(newExpectation);
                    });
            // We need the remove in case there are empty expectations because put is deprecated and
            // putifabsent doesn't replace empty expectations
            if (finalContent.has(CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS)
                && finalContent.get(CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS).isEmpty()) {
              finalContent.remove(CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS);
            }
            finalContent.putIfAbsent(
                CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS, predefinedExpectations);
          }
        }
      } catch (JsonProcessingException e) {
        log.error("Cannot open injector contract", e);
      }
    }
    return finalContent;
  }
}
