package io.openaev.rest.injector_contract;

import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_CARDINALITY;
import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY;
import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS;
import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY_NOT_DYNAMIC;
import static io.openaev.database.model.InjectorContract.DEFAULT_VALUE_FIELD;
import static io.openaev.database.model.InjectorContract.PREDEFINED_EXPECTATIONS;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.*;
import io.openaev.injector_contract.outputs.InjectorContractContentOutputElement;
import jakarta.annotation.Resource;
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

  @Resource protected ObjectMapper mapper;

  public static final String OUTPUTS = "outputs";
  public static final String FIELDS = "fields";
  public static final String MULTIPLE = "n";

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
    return outputParsers.stream()
        .flatMap(outputParser -> outputParser.getContractOutputElements().stream())
        .filter(
            ContractOutputElement
                ::isFinding) // This is related to flag in the UI to compute findings
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
      ObjectNode injectContent = new ObjectMapper().createObjectNode();

      for (JsonNode field : fieldsNode) {
        String key = field.get(CONTRACT_ELEMENT_CONTENT_KEY).asText();

        if (CONTRACT_ELEMENT_CONTENT_KEY_NOT_DYNAMIC.contains(key)) {
          continue;
        }

        JsonNode valueNode;

        // For expectation field, we should use predefinedExpectations
        if (CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS.equals(key)) {
          valueNode = field.get(PREDEFINED_EXPECTATIONS);
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

  public InjectExpectation.EXPECTATION_TYPE[] getPredefinedExpectations(
      InjectorContract injectorContract) {
    ObjectNode convertedContent = injectorContract.getConvertedContent();
    List<InjectExpectation.EXPECTATION_TYPE> predefinedExpectations = new ArrayList<>();

    if (convertedContent == null
        || !convertedContent.has(FIELDS)
        || !convertedContent.get(FIELDS).isArray()) {
      return predefinedExpectations.toArray(new InjectExpectation.EXPECTATION_TYPE[0]);
    }

    ArrayNode fieldsArray = (ArrayNode) convertedContent.get(FIELDS);
    ArrayNode fieldsNode = fieldsArray.deepCopy();
    for (JsonNode field : fieldsNode) {
      String key = field.get(CONTRACT_ELEMENT_CONTENT_KEY).asText();
      if (CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS.equals(key)) {
        predefinedExpectations.add(
            InjectExpectation.EXPECTATION_TYPE.valueOf(
                field.get(PREDEFINED_EXPECTATIONS).asText()));
      }
    }
    return predefinedExpectations.toArray(new InjectExpectation.EXPECTATION_TYPE[0]);
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
      ObjectMapper mapper = new ObjectMapper();
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
}
