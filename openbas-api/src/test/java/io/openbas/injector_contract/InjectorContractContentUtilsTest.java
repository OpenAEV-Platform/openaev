package io.openbas.injector_contract;

import static io.openbas.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_CARDINALITY;
import static io.openbas.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY;
import static io.openbas.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS;
import static io.openbas.database.model.InjectorContract.PREDEFINED_EXPECTATIONS;
import static io.openbas.rest.injector_contract.InjectorContractContentUtils.FIELDS;
import static io.openbas.rest.injector_contract.InjectorContractContentUtils.MULTIPLE;
import static io.openbas.rest.injector_contract.InjectorContractContentUtils.getDynamicInjectorContractFieldsForInject;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openbas.database.model.InjectorContract;
import io.openbas.utils.fixtures.InjectorContractFixture;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

public class InjectorContractContentUtilsTest {

  public static final String EXPECTATION_NAME = "expectation_name";
  public static final String PREVENTION = "Prevention";
  public static final String DETECTION = "Detection";

  private static final ObjectMapper mapper = new ObjectMapper();

  @Test
  public void shouldAddExpectationsWhenPredefinedExpectationsExistent() {
    ArrayNode predefinedExpectations = mapper.createArrayNode();
    predefinedExpectations.add(createExpectation(PREVENTION));
    predefinedExpectations.add(createExpectation(DETECTION));

    ObjectNode content =
        createContentWithField(
            CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS, MULTIPLE, predefinedExpectations);

    InjectorContract contract = InjectorContractFixture.createInjectorContract(content);
    ObjectNode result = getDynamicInjectorContractFieldsForInject(contract);

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
        createContentWithField(
            CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS, MULTIPLE, emptyExpectations);

    InjectorContract contract = InjectorContractFixture.createInjectorContract(content);
    ObjectNode result = getDynamicInjectorContractFieldsForInject(contract);

    assertNotNull(result);
    assertFalse(result.has(CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS));
  }

  @Test
  public void shouldNotAddExpectationsWhenExpectationKeyIsNotDefined() {
    ObjectNode content = mapper.createObjectNode(); // no "fields" -> no key "expectations"

    InjectorContract contract = InjectorContractFixture.createInjectorContract(content);
    ObjectNode result = getDynamicInjectorContractFieldsForInject(contract);

    assertNull(result);
  }

  private ObjectNode createContentWithField(
      String key, String cardinality, ArrayNode predefinedExpectations) {
    ObjectNode field = mapper.createObjectNode();
    field.put(CONTRACT_ELEMENT_CONTENT_KEY, key);
    field.put(CONTRACT_ELEMENT_CONTENT_CARDINALITY, cardinality);
    field.set(PREDEFINED_EXPECTATIONS, predefinedExpectations);

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
