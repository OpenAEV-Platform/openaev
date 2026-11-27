package io.openaev.injector_contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.InjectorContract;
import io.openaev.expectation.ExpectationPropertiesConfig;
import io.openaev.injector_contract.fields.ContractFieldType;
import io.openaev.utils.injector_contract.InjectorContractContentUtils;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The tags and asset group reference fields of a contract, read and remapped without Spring. */
class InjectorContractReferenceFieldsTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final InjectorContractContentUtils utils =
      new InjectorContractContentUtils(mapper, new ExpectationPropertiesConfig());

  private InjectorContract contract() {
    InjectorContract contract = new InjectorContract();
    contract.setContent(
        """
        {"fields": [
          {"key": "labels", "type": "tags"},
          {"key": "target_group", "type": "asset-group-reference", "cardinality": "1"},
          {"key": "other_groups", "type": "asset-group-reference", "cardinality": "n"},
          {"key": "comment", "type": "text"}
        ]}
        """);
    return contract;
  }

  @Test
  @DisplayName("lists the keys of the fields of one type, in contract order")
  void given_contract_should_listTheKeysOfAType() {
    assertEquals(
        List.of("target_group", "other_groups"),
        utils.fieldKeysOfType(contract(), ContractFieldType.AssetGroupReference));
    assertEquals(List.of("labels"), utils.fieldKeysOfType(contract(), ContractFieldType.Tags));
    assertEquals(List.of(), utils.fieldKeysOfType(null, ContractFieldType.Tags));
  }

  @Test
  @DisplayName("reads one id or a list of ids, blank values left out")
  void given_content_should_readTheIdsOfAField() {
    ObjectNode content = mapper.createObjectNode().put("single", "g1").put("blank", " ");
    content.putArray("many").add("g1").add("").add("g2");

    assertEquals(List.of("g1"), InjectorContractContentUtils.idsOf(content, "single"));
    assertEquals(List.of("g1", "g2"), InjectorContractContentUtils.idsOf(content, "many"));
    assertEquals(List.of(), InjectorContractContentUtils.idsOf(content, "blank"));
    assertEquals(List.of(), InjectorContractContentUtils.idsOf(content, "absent"));
    assertEquals(List.of(), InjectorContractContentUtils.idsOf(null, "single"));
  }

  @Test
  @DisplayName("on import, maps tags to the imported tag ids and clears asset group references")
  void given_importedContent_should_remapTagsAndClearGroupReferences() {
    ObjectNode content = mapper.createObjectNode();
    content.putArray("labels").add("old-tag-1").add("old-tag-unknown").add("old-tag-2");
    content.put("target_group", "group-of-another-platform");
    content.putArray("other_groups").add("g1");
    content.put("comment", "kept");
    Map<String, String> importedTags = Map.of("old-tag-1", "new-tag-1", "old-tag-2", "new-tag-2");

    utils.remapImportedReferenceFields(
        contract(), content, oldId -> Optional.ofNullable(importedTags.get(oldId)));

    assertEquals(
        List.of("new-tag-1", "new-tag-2"), InjectorContractContentUtils.idsOf(content, "labels"));
    assertFalse(content.has("target_group"));
    assertFalse(content.has("other_groups"));
    assertTrue(content.has("comment"));
    assertEquals("kept", content.get("comment").asText());
  }
}
