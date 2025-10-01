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
import io.openaev.database.model.InjectorContract;
import io.openaev.injector_contract.outputs.InjectorContractContentOutputElement;
import java.util.List;
import java.util.stream.StreamSupport;
import org.jetbrains.annotations.NotNull;

public class InjectorContractContentUtils {

  public static final String OUTPUTS = "outputs";
  public static final String FIELDS = "fields";
  public static final String MULTIPLE = "n";

  private InjectorContractContentUtils() {}

  /**
   * Function used to get the outputs from the injector contract content.
   *
   * @param content Injector Contract content
   * @param mapper ObjectMapper used to convert JSON to Java objects
   * @return List of ContractOutputElement ( from Injector contract content )
   */
  public static List<InjectorContractContentOutputElement> getContractOutputs(
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
  public static ObjectNode getDynamicInjectorContractFieldsForInject(
      InjectorContract injectorContract) {
    ObjectNode convertedContent = injectorContract.getConvertedContent();

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
}
