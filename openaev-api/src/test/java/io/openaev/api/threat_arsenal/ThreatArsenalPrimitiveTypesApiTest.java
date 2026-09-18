package io.openaev.api.threat_arsenal;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.api.threat_arsenal.dto.PrimitiveTypeDescriptorOutput;
import io.openaev.context.TxCtx;
import io.openaev.database.model.ChainingTypeRegistry;
import io.openaev.database.model.PrimitiveType;
import io.openaev.service.threat_arsenal.ThreatArsenalService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Threat arsenal primitive types API")
class ThreatArsenalPrimitiveTypesApiTest {

  @Mock private ThreatArsenalService threatArsenalService;

  @InjectMocks private ThreatArsenalApi threatArsenalApi;

  @Test
  @DisplayName("Given the descriptor endpoint, should expose every primitive type exactly once")
  void given_descriptorEndpoint_should_exposeEveryPrimitiveTypeOnce() {
    // -- EXECUTE --
    List<PrimitiveTypeDescriptorOutput> descriptors =
        threatArsenalApi.primitiveTypeDescriptors(TxCtx.missing());

    // -- ASSERT --
    assertThat(descriptors)
        .extracting(PrimitiveTypeDescriptorOutput::getPrimitiveType)
        .containsExactlyInAnyOrderElementsOf(ChainingTypeRegistry.getPrimitiveTypes())
        .contains(PrimitiveType.AssetId, PrimitiveType.AssetGroupId);
  }

  @Test
  @DisplayName("Given a serialized descriptor, should expose each capability under a single key")
  void given_serializedDescriptor_should_exposeEachCapabilityUnderASingleKey() throws Exception {
    // -- PREPARE --
    PrimitiveTypeDescriptorOutput descriptor =
        threatArsenalApi.primitiveTypeDescriptors(TxCtx.missing()).getFirst();

    // -- EXECUTE --
    JsonNode capabilities =
        new ObjectMapper().valueToTree(descriptor).get("primitive_type_capabilities");

    // -- ASSERT --
    // A primitive `boolean isNumericValue` would make Jackson emit two properties: Lombok names
    // the getter `isNumericValue()`, from which Jackson infers `numericValue`, which no longer
    // matches the field. The Boolean wrapper yields `getIsNumericValue()`, which does.
    Iterable<String> keys = capabilities::fieldNames;
    assertThat(keys).containsExactlyInAnyOrder("is_numeric_value", "is_case_sensitivity");
  }
}
