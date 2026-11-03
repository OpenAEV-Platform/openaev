package io.openaev.injector_contract;

import static io.openaev.helper.SupportedLanguage.en;
import static io.openaev.helper.SupportedLanguage.fr;
import static io.openaev.injector_contract.fields.ContractAsset.assetField;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.Endpoint;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ContractSerializationTest {

  private static final ObjectMapper mapper = new ObjectMapper();

  private static Contract executableContract() {
    ContractConfig config =
        new ContractConfig(
            "openaev_implant",
            Map.of(en, "OpenAEV Implant", fr, "OpenAEV Implant"),
            "#000000",
            "#000000",
            "/img/icon-openaev_implant.png");
    return Contract.executableContract(
        config,
        UUID.randomUUID().toString(),
        Map.of(en, "netstat", fr, "netstat"),
        List.of(assetField(ContractCardinality.Multiple)),
        List.of(Endpoint.PLATFORM_TYPE.Windows),
        true,
        null);
  }

  @Nested
  @DisplayName("Atomic testing flag in the contract content")
  class AtomicTestingFlagTest {

    @Test
    @DisplayName("given a contract usable for atomic testing should write a single key set to true")
    void given_atomicTestingContract_should_writeSingleKeyTrue() throws Exception {
      // Arrange
      Contract contract = executableContract();

      // Act
      JsonNode content = mapper.readTree(mapper.writeValueAsString(contract));

      // Assert: only the snake_case key, no "atomicTesting" duplicate from the Lombok getter
      assertTrue(content.get("is_atomic_testing").booleanValue());
      assertFalse(content.has("atomicTesting"));
    }

    @Test
    @DisplayName(
        "given a contract not usable for atomic testing should write a single key set to false")
    void given_nonAtomicTestingContract_should_writeSingleKeyFalse() throws Exception {
      // Arrange
      Contract contract = executableContract();
      contract.setAtomicTesting(false);

      // Act
      JsonNode content = mapper.readTree(mapper.writeValueAsString(contract));

      // Assert
      assertFalse(content.get("is_atomic_testing").booleanValue());
      assertFalse(content.has("atomicTesting"));
    }
  }
}
