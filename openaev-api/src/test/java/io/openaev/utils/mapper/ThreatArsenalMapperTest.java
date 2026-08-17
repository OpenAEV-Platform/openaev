package io.openaev.utils.mapper;

import static io.openaev.database.model.InjectorContract.AVAILABLE_EXPECTATIONS;
import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY;
import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS;
import static io.openaev.database.model.InjectorContract.IS_PREDEFINED_EXPECTATION;
import static io.openaev.utils.injector_contract.InjectorContractContentUtils.FIELDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.api.threat_arsenal.dto.ThreatArsenalActionFullOutput;
import io.openaev.api.threat_arsenal.dto.ThreatArsenalExpectationDetail;
import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.InjectorContract;
import io.openaev.utils.injector_contract.InjectorContractContentUtils;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Threat arsenal mapper tests")
class ThreatArsenalMapperTest {

  private static final ObjectMapper mapper = new ObjectMapper();

  @Mock private PayloadMapper payloadMapper;
  @Mock private EntityManager entityManager;

  private ThreatArsenalMapper buildMapper() {
    // Real content utils: the predefined-expectation readers only walk the contract's
    // converted content, so no Spring context is needed.
    return new ThreatArsenalMapper(
        payloadMapper, new InjectorContractContentUtils(), entityManager);
  }

  /** One predefined expectation node as the contract content serializes it. */
  private static ObjectNode expectationNode(
      final String type, final String name, final String description, final Integer order) {
    ObjectNode node = mapper.createObjectNode();
    node.put(IS_PREDEFINED_EXPECTATION, true);
    node.put("expectation_type", type);
    if (name != null) {
      node.put("expectation_name", name);
    }
    if (description != null) {
      node.put("expectation_description", description);
    }
    if (order != null) {
      node.put("expectation_order", order);
    }
    return node;
  }

  /** A minimal injector contract whose content declares the given predefined expectations. */
  private static InjectorContract contractWith(final ObjectNode... expectations) {
    ArrayNode available = mapper.createArrayNode();
    for (ObjectNode expectation : expectations) {
      available.add(expectation);
    }
    ObjectNode field = mapper.createObjectNode();
    field.put(CONTRACT_ELEMENT_CONTENT_KEY, CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS);
    field.set(AVAILABLE_EXPECTATIONS, available);
    ArrayNode fields = mapper.createArrayNode();
    fields.add(field);
    ObjectNode content = mapper.createObjectNode();
    content.set(FIELDS, fields);

    InjectorContract contract = new InjectorContract();
    contract.setId("contract-1");
    contract.setLabels(Map.of("en", "Phishing"));
    contract.setConvertedContent(content);
    return contract;
  }

  @Test
  @DisplayName(
      "toThreatArsenalActionFullOutput should expose named expectation details sorted by declared"
          + " order")
  void fullOutput_should_exposeOrderedExpectationDetails() {
    // -- ARRANGE -- phishing-style steps declared out of order in the content (submission
    // first), each carrying the kill-chain position the executor stamps.
    InjectorContract contract =
        contractWith(
            expectationNode("MANUAL", "Credentials not submitted", "No data submitted", 2),
            expectationNode("MANUAL", "Email not opened", "The email was not opened", 0),
            expectationNode("MANUAL", "Link not clicked", "The link was not followed", 1));

    // -- ACT --
    ThreatArsenalActionFullOutput output = buildMapper().toThreatArsenalActionFullOutput(contract);

    // -- ASSERT -- the drawer receives the steps in kill-chain order, with their real names.
    List<ThreatArsenalExpectationDetail> details = output.expectationDetails();
    assertEquals(3, details.size());
    assertEquals("Email not opened", details.get(0).name());
    assertEquals("Link not clicked", details.get(1).name());
    assertEquals("Credentials not submitted", details.get(2).name());
    assertEquals("The email was not opened", details.get(0).description());
    assertEquals(0, details.get(0).order());
    assertEquals(BaseInjectExpectation.EXPECTATION_TYPE.MANUAL, details.get(0).type());
  }

  @Test
  @DisplayName(
      "toThreatArsenalActionFullOutput should sort legacy expectations without an order by name")
  void fullOutput_should_fallBackToNameOrder() {
    // -- ARRANGE -- legacy contract created before expectation_order existed.
    InjectorContract contract =
        contractWith(
            expectationNode("PREVENTION", "Prevention", null, null),
            expectationNode("DETECTION", "Detection", null, null));

    // -- ACT --
    ThreatArsenalActionFullOutput output = buildMapper().toThreatArsenalActionFullOutput(contract);

    // -- ASSERT --
    List<ThreatArsenalExpectationDetail> details = output.expectationDetails();
    assertEquals(2, details.size());
    assertEquals("Detection", details.get(0).name());
    assertEquals("Prevention", details.get(1).name());
    assertNull(details.get(0).order(), "legacy expectations carry no declared order");
  }

  @Test
  @DisplayName(
      "toThreatArsenalActionFullOutput should return null details when the contract declares no"
          + " predefined expectations")
  void fullOutput_should_returnNullDetailsWhenContractDeclaresNone() {
    // -- ARRANGE --
    InjectorContract contract = contractWith();

    // -- ACT --
    ThreatArsenalActionFullOutput output = buildMapper().toThreatArsenalActionFullOutput(contract);

    // -- ASSERT -- null (not empty) so readers fall back to the bare action_expectations types.
    assertNull(output.expectationDetails());
  }
}
