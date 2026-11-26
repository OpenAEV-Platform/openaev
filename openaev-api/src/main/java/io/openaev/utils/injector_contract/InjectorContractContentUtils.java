package io.openaev.utils.injector_contract;

import static io.openaev.database.model.InjectorContract.*;
import static io.openaev.utils.mapper.InjectExpectationMapper.NODE_EXPECTATION_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.*;
import io.openaev.expectation.ExpectationPropertiesConfig;
import io.openaev.helper.InjectModelHelper;
import io.openaev.injector_contract.fields.ContractFieldType;
import io.openaev.injector_contract.outputs.InjectorContractContentOutputElement;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import java.util.function.Function;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class InjectorContractContentUtils {

  private final ObjectMapper mapper;
  private final ExpectationPropertiesConfig expectationPropertiesConfig;

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

  public void resetExpirationTimesInContractContent(InjectorContract contract) {
    ObjectNode convertedContent = contract.getConvertedContent();
    if (convertedContent == null
        || !convertedContent.has(FIELDS)
        || !convertedContent.get(FIELDS).isArray()) {
      return;
    }

    for (JsonNode field : convertedContent.get(FIELDS)) {
      String key = field.get(CONTRACT_ELEMENT_CONTENT_KEY).asText();
      if (CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS.equals(key)) {
        JsonNode available = field.get(AVAILABLE_EXPECTATIONS);
        if (available != null && available.isArray()) {
          StreamSupport.stream(available.spliterator(), false)
              .filter(
                  exp ->
                      exp.has(IS_PREDEFINED_EXPECTATION)
                          && exp.get(IS_PREDEFINED_EXPECTATION).asBoolean())
              .forEach(exp -> overrideExpirationTimes(exp));
        }
      }
    }
  }

  private JsonNode overrideExpirationTimes(JsonNode exp) {
    if (exp.isObject()) {
      ObjectNode objExp = (ObjectNode) exp;
      JsonNode expTypeNode = objExp.get(CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS_TYPE);
      if (!(expTypeNode instanceof NullNode)) {
        String expTypeString = expTypeNode.asText();
        try {
          BaseInjectExpectation.EXPECTATION_TYPE expType =
              BaseInjectExpectation.EXPECTATION_TYPE.valueOf(expTypeString);
          objExp.put(
              CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS_EXPIRATION_TIME,
              expectationPropertiesConfig.getExpirationTimeByType(expType));
        } catch (IllegalArgumentException e) {
          log.debug("Expectation type '{}' is not valid", expTypeString, e);
        }
      }
    }
    return exp;
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
    return fields(injectorContract).containsKey(field);
  }

  /**
   * The keys of the fields an inject must fill, by group: any one field of a group fills it. A
   * mandatory field is a group of its own; a field mandatory on a condition counts when the inject
   * meets it.
   */
  public Set<Set<String>> mandatoryFieldGroups(Inject inject) {
    return mandatoryFieldGroups(
        fields(inject.getInjectorContract().orElse(null)),
        field ->
            InjectModelHelper.getFieldValue(
                inject.getTeams().stream().map(Team::getId).toList(),
                inject.getAssets().stream().map(Asset::getId).toList(),
                inject.getAssetGroups().stream().map(AssetGroup::getId).toList(),
                field,
                inject.getContent()));
  }

  /** The same groups among these contract fields, reading the inject's values with valuesOf. */
  public static Set<Set<String>> mandatoryFieldGroups(
      Map<String, JsonNode> fields, Function<JsonNode, List<String>> valuesOf) {
    Set<Set<String>> groups = new LinkedHashSet<>();
    fields.forEach(
        (key, field) -> {
          if (field.path(CONTRACT_ELEMENT_CONTENT_MANDATORY).asBoolean()
              || isMandatoryOnCondition(field, fields, valuesOf)) {
            groups.add(Set.of(key));
          }
          Set<String> group = new LinkedHashSet<>();
          field
              .path(CONTRACT_ELEMENT_CONTENT_MANDATORY_GROUPS)
              .forEach(member -> group.add(member.asText()));
          if (!group.isEmpty()) {
            groups.add(group);
          }
        });
    return groups;
  }

  private static boolean isMandatoryOnCondition(
      JsonNode field, Map<String, JsonNode> fields, Function<JsonNode, List<String>> valuesOf) {
    JsonNode values = field.path(CONTRACT_ELEMENT_CONTENT_MANDATORY_CONDITIONAL_VALUES);
    return StreamSupport.stream(
            field.path(CONTRACT_ELEMENT_CONTENT_MANDATORY_CONDITIONAL_FIELDS).spliterator(), false)
        .map(JsonNode::asText)
        .filter(fields::containsKey)
        .anyMatch(
            key ->
                !values.has(key)
                    || isConditionMet(values.get(key), valuesOf.apply(fields.get(key))));
  }

  private static boolean isConditionMet(JsonNode expected, List<String> actualValues) {
    List<String> expectedValues = new ArrayList<>();
    if (expected.isArray()) {
      expected.forEach(value -> expectedValues.add(value.asText()));
    } else {
      expectedValues.add(expected.asText());
    }
    return actualValues.stream().anyMatch(expectedValues::contains);
  }

  /** The contract's fields by key, empty when its content cannot be read. */
  public Map<String, JsonNode> fields(InjectorContract injectorContract) {
    Map<String, JsonNode> fields = new LinkedHashMap<>();
    if (injectorContract == null || injectorContract.getContent() == null) {
      return fields;
    }
    try {
      mapper
          .readTree(injectorContract.getContent())
          .path(FIELDS)
          .forEach(
              field -> {
                if (field.has(CONTRACT_ELEMENT_CONTENT_KEY)) {
                  fields.put(field.get(CONTRACT_ELEMENT_CONTENT_KEY).asText(), field);
                }
              });
    } catch (JsonProcessingException e) {
      return new LinkedHashMap<>();
    }
    return fields;
  }

  /** The keys of the contract's fields of the given type, in contract order. */
  public List<String> fieldKeysOfType(InjectorContract injectorContract, ContractFieldType type) {
    List<String> keys = new ArrayList<>();
    fields(injectorContract)
        .forEach(
            (key, field) -> {
              if (type.label.equals(field.path(CONTRACT_ELEMENT_CONTENT_TYPE).asText())) {
                keys.add(key);
              }
            });
    return keys;
  }

  /**
   * The ids an inject content holds under a field key: one id for a cardinality 1 field, the list
   * for a cardinality n one; blank values are left out.
   */
  public static List<String> idsOf(JsonNode content, String key) {
    List<String> ids = new ArrayList<>();
    if (content == null) {
      return ids;
    }
    JsonNode value = content.get(key);
    if (value == null || value.isNull()) {
      return ids;
    }
    if (value.isArray()) {
      value.forEach(
          item -> {
            if (item.isTextual() && !item.asText().isBlank()) {
              ids.add(item.asText());
            }
          });
    } else if (value.isTextual() && !value.asText().isBlank()) {
      ids.add(value.asText());
    }
    return ids;
  }

  /**
   * Rewrites the reference fields of an imported inject content in place: a tags field keeps the
   * ids of the imported tags, given by importedTagId (an id with no imported tag is dropped), and
   * an asset group reference field is cleared, since asset groups are not part of the export and an
   * id of another platform names nothing here; the form's required rule then asks for it again.
   */
  public void remapImportedReferenceFields(
      InjectorContract injectorContract,
      ObjectNode content,
      Function<String, Optional<String>> importedTagId) {
    if (content == null) {
      return;
    }
    for (String key : fieldKeysOfType(injectorContract, ContractFieldType.Tags)) {
      if (!content.has(key)) {
        continue;
      }
      ArrayNode remapped = mapper.createArrayNode();
      idsOf(content, key).stream()
          .map(importedTagId)
          .flatMap(Optional::stream)
          .forEach(remapped::add);
      content.set(key, remapped);
    }
    for (String key : fieldKeysOfType(injectorContract, ContractFieldType.AssetGroupReference)) {
      content.remove(key);
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
