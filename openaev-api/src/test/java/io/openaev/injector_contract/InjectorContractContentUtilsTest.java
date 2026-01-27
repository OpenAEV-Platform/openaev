package io.openaev.injector_contract;

import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_CARDINALITY;
import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY;
import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY_ASSETS;
import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY_ASSET_GROUPS;
import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS;
import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_MANDATORY;
import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_TYPE;
import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_TYPE_ASSET;
import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_TYPE_ASSET_GROUP;
import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_TYPE_EXPECTATION;
import static io.openaev.database.model.InjectorContract.PREDEFINED_EXPECTATIONS;
import static io.openaev.rest.injector_contract.InjectorContractContentUtils.FIELDS;
import static io.openaev.rest.injector_contract.InjectorContractContentUtils.MULTIPLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.InjectorContract;
import io.openaev.rest.injector_contract.InjectorContractContentUtils;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utilstest.RabbitMQTestListener;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;

@SpringBootTest
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
public class InjectorContractContentUtilsTest {

  public static final String EXPECTATION_NAME = "expectation_name";
  public static final String PREVENTION = "Prevention";
  public static final String DETECTION = "Detection";

  private static final ObjectMapper mapper = new ObjectMapper();

  @Autowired private InjectorContractContentUtils injectorContractContentUtils;

  @Test
  public void shouldAddExpectationsWhenPredefinedExpectationsExistent() {
    ArrayNode predefinedExpectations = mapper.createArrayNode();
    predefinedExpectations.add(createExpectation(PREVENTION));
    predefinedExpectations.add(createExpectation(DETECTION));

    ObjectNode content =
        createContentWithFieldExpectations(
            CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS,
            CONTRACT_ELEMENT_CONTENT_TYPE_EXPECTATION,
            MULTIPLE,
            predefinedExpectations);

    InjectorContract contract = InjectorContractFixture.createInjectorContract(content);
    ObjectNode result =
        injectorContractContentUtils.getDynamicInjectorContractFieldsForInject(contract);

    assertNotNull(result);
    assertTrue(result.has(CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS));

    JsonNode expectations = result.get(CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS);
    assertEquals(predefinedExpectations.size(), expectations.size());

    // Compare added expectations
    Set<String> expectedExpectations =
        StreamSupport.stream(predefinedExpectations.spliterator(), false)
            .map(e -> e.get(EXPECTATION_NAME).asText())
            .collect(Collectors.toSet());

    Set<String> actualExpectations =
        StreamSupport.stream(expectations.spliterator(), false)
            .map(e -> e.get(EXPECTATION_NAME).asText())
            .collect(Collectors.toSet());

    assertEquals(expectedExpectations, actualExpectations);
  }

  @Test
  public void shouldNotAddExpectationsWhenPredefinedExpectationsAreEmpty() {
    ArrayNode emptyExpectations = mapper.createArrayNode(); // empty array

    ObjectNode content =
        createContentWithFieldExpectations(
            CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS,
            CONTRACT_ELEMENT_CONTENT_TYPE_EXPECTATION,
            MULTIPLE,
            emptyExpectations);

    InjectorContract contract = InjectorContractFixture.createInjectorContract(content);
    ObjectNode result =
        injectorContractContentUtils.getDynamicInjectorContractFieldsForInject(contract);

    assertNotNull(result);
    assertFalse(result.has(CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS));
  }

  @Test
  public void shouldNotAddExpectationsWhenExpectationKeyIsNotDefined() {
    ObjectNode content = mapper.createObjectNode(); // no "fields" -> no key "expectations"

    InjectorContract contract = InjectorContractFixture.createInjectorContract(content);
    ObjectNode result =
        injectorContractContentUtils.getDynamicInjectorContractFieldsForInject(contract);

    assertNull(result);
  }

  public static ObjectNode createContentWithFieldAsset() {
    ObjectNode field =
        createContentWithField(
            CONTRACT_ELEMENT_CONTENT_KEY_ASSETS, CONTRACT_ELEMENT_CONTENT_TYPE_ASSET, MULTIPLE);
    return wrapFieldInContent(field);
  }

  public static ObjectNode createContentWithFieldAssetGroup() {
    ObjectNode field =
        createContentWithField(
            CONTRACT_ELEMENT_CONTENT_KEY_ASSET_GROUPS,
            CONTRACT_ELEMENT_CONTENT_TYPE_ASSET_GROUP,
            MULTIPLE);
    return wrapFieldInContent(field);
  }

  public static ObjectNode createContentWithFieldExpectations(
      String key, String type, String cardinality, ArrayNode predefinedExpectations) {
    ObjectNode field = createContentWithField(key, type, cardinality);
    field.set(PREDEFINED_EXPECTATIONS, predefinedExpectations);
    return wrapFieldInContent(field);
  }

  private static ObjectNode createContentWithField(String key, String type, String cardinality) {
    ObjectNode field = mapper.createObjectNode();
    field.put(CONTRACT_ELEMENT_CONTENT_KEY, key);
    field.put(CONTRACT_ELEMENT_CONTENT_TYPE, type);
    field.put(CONTRACT_ELEMENT_CONTENT_CARDINALITY, cardinality);
    field.put(CONTRACT_ELEMENT_CONTENT_MANDATORY, false);
    return field;
  }

  private static ObjectNode wrapFieldInContent(ObjectNode field) {
    ArrayNode fieldsArray = mapper.createArrayNode();
    fieldsArray.add(field);

    ObjectNode content = mapper.createObjectNode();
    content.set(FIELDS, fieldsArray);

    return content;
  }

  private ObjectNode createExpectation(String name) {
    ObjectNode expectation = mapper.createObjectNode();
    expectation.put(EXPECTATION_NAME, name);
    return expectation;
  }
}
