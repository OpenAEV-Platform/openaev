package io.openaev.utils.injector_contract;

import static io.openaev.database.model.InjectorContract.CONTRACT_CONTENT_KEY_CONTRACT_ID;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.InjectorContract;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Injector contract migration utils")
class InjectorContractMigrationUtilsTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String CONTRACT_ID = "d5b0b8e0-9d3a-4d9f-8f10-0f1a2b3c4d5e";

  private InjectorContract buildContract(String content) {
    InjectorContract contract = new InjectorContract();
    contract.setId(CONTRACT_ID);
    contract.setContent(content);
    return contract;
  }

  private ObjectNode parseContent(InjectorContract contract) throws JsonProcessingException {
    return (ObjectNode) MAPPER.readTree(contract.getContent());
  }

  @Test
  @DisplayName("Should rewrite a stale embedded contract_id to the entity id")
  void given_contentWithStaleContractId_should_rewriteToEntityId() throws Exception {
    InjectorContract contract =
        buildContract("{\"contract_id\":\"stale-exported-id\",\"fields\":[]}");

    InjectorContractMigrationUtils.migratePredefinedExpectations(contract);

    assertEquals(
        CONTRACT_ID, parseContent(contract).get(CONTRACT_CONTENT_KEY_CONTRACT_ID).asText());
    assertEquals(
        CONTRACT_ID, contract.getConvertedContent().get(CONTRACT_CONTENT_KEY_CONTRACT_ID).asText());
  }

  @Test
  @DisplayName("Should add contract_id when the content JSON has no contract_id key")
  void given_contentWithoutContractId_should_addContractId() throws Exception {
    InjectorContract contract = buildContract("{\"fields\":[]}");

    InjectorContractMigrationUtils.migratePredefinedExpectations(contract);

    assertEquals(
        CONTRACT_ID, parseContent(contract).get(CONTRACT_CONTENT_KEY_CONTRACT_ID).asText());
  }

  @Test
  @DisplayName("Should be idempotent when re-run on already migrated content")
  void given_alreadyMigratedContent_should_beIdempotent() throws Exception {
    InjectorContract contract =
        buildContract("{\"contract_id\":\"stale-exported-id\",\"fields\":[]}");

    InjectorContractMigrationUtils.migratePredefinedExpectations(contract);
    String contentAfterFirstRun = contract.getContent();
    InjectorContractMigrationUtils.migratePredefinedExpectations(contract);

    assertEquals(contentAfterFirstRun, contract.getContent());
    assertEquals(
        CONTRACT_ID, parseContent(contract).get(CONTRACT_CONTENT_KEY_CONTRACT_ID).asText());
  }

  @Test
  @DisplayName("Should leave malformed content untouched without throwing")
  void given_malformedContent_should_leaveContentUntouched() {
    InjectorContract contract = buildContract("not a json document");

    assertDoesNotThrow(
        () -> InjectorContractMigrationUtils.migratePredefinedExpectations(contract));

    assertEquals("not a json document", contract.getContent());
  }

  @Test
  @DisplayName("Should leave content without a fields key untouched")
  void given_contentWithoutFields_should_leaveContentUntouched() throws Exception {
    InjectorContract contract = buildContract("{\"contract_id\":\"stale-exported-id\"}");

    InjectorContractMigrationUtils.migratePredefinedExpectations(contract);

    assertEquals(
        "stale-exported-id", parseContent(contract).get(CONTRACT_CONTENT_KEY_CONTRACT_ID).asText());
  }

  @Test
  @DisplayName("Should rewrite contract_id while migrating legacy predefined expectations")
  void given_legacyPredefinedExpectations_should_migrateAndRewriteContractId() throws Exception {
    String legacyContent =
        "{\"contract_id\":\"stale-exported-id\",\"fields\":[{\"key\":\"expectations\","
            + "\"predefinedExpectations\":[{\"expectation_type\":\"PREVENTION\"}]}]}";
    InjectorContract contract = buildContract(legacyContent);

    InjectorContractMigrationUtils.migratePredefinedExpectations(contract);

    ObjectNode content = parseContent(contract);
    assertEquals(CONTRACT_ID, content.get(CONTRACT_CONTENT_KEY_CONTRACT_ID).asText());
    ObjectNode expectationsField = (ObjectNode) content.get("fields").get(0);
    assertFalse(expectationsField.has("predefinedExpectations"));
    assertEquals(
        "PREVENTION",
        expectationsField
            .get(InjectorContract.AVAILABLE_EXPECTATIONS)
            .get(0)
            .get("expectation_type")
            .asText());
  }
}
