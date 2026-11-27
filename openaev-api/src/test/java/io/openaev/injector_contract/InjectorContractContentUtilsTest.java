package io.openaev.injector_contract;

import static io.openaev.database.model.InjectorContract.AVAILABLE_EXPECTATIONS;
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
import static io.openaev.database.model.InjectorContract.IS_PREDEFINED_EXPECTATION;
import static io.openaev.utils.injector_contract.InjectorContractContentUtils.FIELDS;
import static io.openaev.utils.injector_contract.InjectorContractContentUtils.MULTIPLE;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.ContractOutputType;
import io.openaev.database.model.InjectorContract;
import io.openaev.expectation.ExpectationPropertiesConfig;
import io.openaev.injector_contract.outputs.InjectorContractContentOutputElement;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.injector_contract.InjectorContractContentUtils;
import io.openaev.utilstest.RabbitMQTestListener;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

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
  @MockitoSpyBean private ExpectationPropertiesConfig expectationPropertiesConfig;

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
    // Mark each expectation as predefined before setting as availableExpectations
    for (JsonNode exp : predefinedExpectations) {
      ((ObjectNode) exp).put(IS_PREDEFINED_EXPECTATION, true);
    }
    field.set(AVAILABLE_EXPECTATIONS, predefinedExpectations);
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

  @Nested
  @DisplayName("Expiration times")
  class ExpirationTimes {
    private InjectorContract getContractWithContents(String contents)
        throws JsonProcessingException {
      InjectorContract contract = InjectorContractFixture.createDefaultInjectorContract();
      contract.setContent(contents);
      contract.setConvertedContent(
          contents == null ? null : (ObjectNode) mapper.readTree(contents));
      return contract;
    }

    private InjectorContract getContractWithExpectationsContents() throws JsonProcessingException {
      return getContractWithContents(
          """
               {
                  "fields" : [ {
                    "key" : "expectations",
                    "label" : "Expectations",
                    "mandatory" : false,
                    "readOnly" : false,
                    "mandatoryGroups" : null,
                    "mandatoryConditionFields" : null,
                    "mandatoryConditionValues" : null,
                    "visibleConditionFields" : null,
                    "visibleConditionValues" : null,
                    "linkedFields" : [ ],
                    "linkedValues" : [ ],
                    "argumentType" : null,
                    "cardinality" : "n",
                    "defaultValue" : [ ],
                    "availableExpectations" : [ {
                      "expectation_type" : "DETECTION",
                      "expectation_name" : "Detection",
                      "expectation_description" : null,
                      "expectation_score" : 100.0,
                      "expectation_expectation_group" : false,
                      "expectation_expiration_time" : 3600,
                      "expectation_is_multi_selectable" : false,
                      "expectation_is_predefined" : true,
                      "expectation_order" : null,
                      "expectation_expected_security_platform_types" : [ "EDR" ]
                    }, {
                      "expectation_type" : "PREVENTION",
                      "expectation_name" : "Prevention",
                      "expectation_description" : null,
                      "expectation_score" : 100.0,
                      "expectation_expectation_group" : false,
                      "expectation_expiration_time" : 3600,
                      "expectation_is_multi_selectable" : false,
                      "expectation_is_predefined" : true,
                      "expectation_order" : null,
                      "expectation_expected_security_platform_types" : [ "EDR" ]
                    }, {
                      "expectation_type" : "LOGGING",
                      "expectation_name" : "Logging",
                      "expectation_description" : null,
                      "expectation_score" : 100.0,
                      "expectation_expectation_group" : false,
                      "expectation_expiration_time" : 3600,
                      "expectation_is_multi_selectable" : false,
                      "expectation_is_predefined" : true,
                      "expectation_order" : null,
                      "expectation_expected_security_platform_types" : [ "EDR" ]
                    }, {
                      "expectation_type" : "VULNERABILITY",
                      "expectation_name" : "Not vulnerable",
                      "expectation_description" : null,
                      "expectation_score" : 100.0,
                      "expectation_expectation_group" : false,
                      "expectation_expiration_time" : 3600,
                      "expectation_is_multi_selectable" : false,
                      "expectation_is_predefined" : true,
                      "expectation_order" : null,
                      "expectation_expected_security_platform_types" : [ "VULNERABILITY_SCANNER" ]
                    }, {
                      "expectation_type" : "MANUAL",
                      "expectation_name" : "Not vulnerable",
                      "expectation_description" : null,
                      "expectation_score" : 100.0,
                      "expectation_expectation_group" : false,
                      "expectation_expiration_time" : 3600,
                      "expectation_is_multi_selectable" : false,
                      "expectation_is_predefined" : false,
                      "expectation_order" : null,
                      "expectation_expected_security_platform_types" : [ "SIEM" ]
                    } ],
                    "type" : "expectation"
                  } ]
                }
              """);
    }

    private InjectorContract getContractWithEmptyContents() throws JsonProcessingException {
      return getContractWithContents(null);
    }

    private InjectorContract getContractWithMissingFieldsContents() throws JsonProcessingException {
      return getContractWithContents("{}");
    }

    private InjectorContract getContractWithFieldsNotArrayContents()
        throws JsonProcessingException {
      return getContractWithContents(
          """
      {
        "fields": { }
      }
      """);
    }

    private InjectorContract getContractWithMissingExpectationsContents()
        throws JsonProcessingException {
      return getContractWithContents(
          """
      {
        "fields": [ {
          "key": "not-what-we-look-for"
         } ]
      }
      """);
    }

    private InjectorContract getContractWithMissingAvailableExpectationsField()
        throws JsonProcessingException {
      return getContractWithContents(
          """
      {
        "fields": [ {
          "key": "expectations"
         } ]
      }
      """);
    }

    private InjectorContract getContractWithAvailableExpectationsNotArrayField()
        throws JsonProcessingException {
      return getContractWithContents(
          """
      {
        "fields": [ {
          "key": "expectations",
          "availableExpectations": {}
         } ]
      }
      """);
    }

    @Test
    @DisplayName(
        "Reset expectation expiration times when missing contents returns contract unchanged")
    void noContentsReturnContractUnchanged() throws JsonProcessingException {
      InjectorContract contract = getContractWithEmptyContents();

      injectorContractContentUtils.resetExpirationTimesInContractContent(contract);

      assertThatJson(contract.getConvertedContent()).isNull();
    }

    @Test
    @DisplayName(
        "Reset expectation expiration times when missing fields attribute in contents returns contract unchanged")
    void noFieldsReturnContractUnchanged() throws JsonProcessingException {
      InjectorContract contract = getContractWithMissingFieldsContents();

      injectorContractContentUtils.resetExpirationTimesInContractContent(contract);

      assertThatJson(contract.getConvertedContent()).isEqualTo("{}");
    }

    @Test
    @DisplayName(
        "Reset expectation expiration times when fields attribute not an array returns contract unchanged")
    void fieldsNotArrayReturnContractUnchanged() throws JsonProcessingException {
      InjectorContract contract = getContractWithFieldsNotArrayContents();

      injectorContractContentUtils.resetExpirationTimesInContractContent(contract);

      assertThatJson(contract.getConvertedContent()).isEqualTo("{\"fields\":{}}");
    }

    @Test
    @DisplayName(
        "Reset expectation expiration times when no expectations contents returns contract unchanged")
    void noExpectationsReturnContractUnchanged() throws JsonProcessingException {
      InjectorContract contract = getContractWithMissingExpectationsContents();

      injectorContractContentUtils.resetExpirationTimesInContractContent(contract);

      assertThatJson(contract.getConvertedContent())
          .isEqualTo("{\"fields\":[{\"key\":\"not-what-we-look-for\"}]}");
    }

    @Test
    @DisplayName(
        "Reset expectation expiration times when no available expectations field returns contract unchanged")
    void noAvailableExpectationsReturnContractUnchanged() throws JsonProcessingException {
      InjectorContract contract = getContractWithMissingAvailableExpectationsField();

      injectorContractContentUtils.resetExpirationTimesInContractContent(contract);

      assertThatJson(contract.getConvertedContent())
          .isEqualTo("{\"fields\":[{\"key\":\"expectations\"}]}");
    }

    @Test
    @DisplayName(
        "Reset expectation expiration times when available expectations not an array returns contract unchanged")
    void availableExpectationsNotArrayReturnContractUnchanged() throws JsonProcessingException {
      InjectorContract contract = getContractWithAvailableExpectationsNotArrayField();

      injectorContractContentUtils.resetExpirationTimesInContractContent(contract);

      assertThatJson(contract.getConvertedContent())
          .isEqualTo("{\"fields\":[{\"key\":\"expectations\",\"availableExpectations\":{}}]}");
    }

    @Test
    @DisplayName("Reset expectation expiration times in place in injector contract content")
    void shouldResetExpirationTimes() throws JsonProcessingException {
      InjectorContract contract = getContractWithExpectationsContents();

      BigDecimal expectedDetectionExpirationTime = new BigDecimal(241);
      when(expectationPropertiesConfig.getDetectionExpirationTime())
          .thenReturn(expectedDetectionExpirationTime.longValue());
      BigDecimal expectedPreventionExpirationTime = new BigDecimal(361);
      when(expectationPropertiesConfig.getPreventionExpirationTime())
          .thenReturn(expectedPreventionExpirationTime.longValue());
      BigDecimal expectedLoggingExpirationTime = new BigDecimal(481);
      when(expectationPropertiesConfig.getLoggingExpirationTime())
          .thenReturn(expectedLoggingExpirationTime.longValue());
      BigDecimal expectedVulnerabilityExpirationTime = new BigDecimal(601);
      when(expectationPropertiesConfig.getVulnerabilityExpirationTime())
          .thenReturn(expectedVulnerabilityExpirationTime.longValue());

      injectorContractContentUtils.resetExpirationTimesInContractContent(contract);

      assertThatJson(contract.getConvertedContent())
          .inPath("fields.[0].availableExpectations.[0].expectation_expiration_time")
          .asNumber()
          .isEqualTo(expectedDetectionExpirationTime);
      assertThatJson(contract.getConvertedContent())
          .inPath("fields.[0].availableExpectations.[1].expectation_expiration_time")
          .asNumber()
          .isEqualTo(expectedPreventionExpirationTime);
      assertThatJson(contract.getConvertedContent())
          .inPath("fields.[0].availableExpectations.[2].expectation_expiration_time")
          .asNumber()
          .isEqualTo(expectedLoggingExpirationTime);
      assertThatJson(contract.getConvertedContent())
          .inPath("fields.[0].availableExpectations.[3].expectation_expiration_time")
          .asNumber()
          .isEqualTo(expectedVulnerabilityExpirationTime);
    }
  }

  @Test
  void shouldDeserializeAssetTypeFromContract() throws Exception {

    String contractJson =
        """
    {
      "outputs": [
        {
          "field": "found_assets",
          "isFindingCompatible": false,
          "isMultiple": true,
          "labels": ["shodan"],
          "type": "asset"
        }
      ]
    }
    """;

    ObjectNode content = (ObjectNode) mapper.readTree(contractJson);

    List<InjectorContractContentOutputElement> outputs =
        injectorContractContentUtils.getContractOutputs(content, mapper);

    assertEquals(1, outputs.size());

    InjectorContractContentOutputElement output = outputs.get(0);

    assertEquals("found_assets", output.getField());
    assertEquals(ContractOutputType.Asset, output.getType());
    assertFalse(output.isFindingCompatible());
    assertTrue(output.isMultiple());
  }
}
