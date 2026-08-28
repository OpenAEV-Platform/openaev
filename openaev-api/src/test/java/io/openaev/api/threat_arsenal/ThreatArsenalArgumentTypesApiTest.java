package io.openaev.api.threat_arsenal;

import static org.assertj.core.api.Assertions.assertThat;

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
@DisplayName("Threat arsenal argument types API")
class ThreatArsenalArgumentTypesApiTest {

  @Mock private ThreatArsenalService threatArsenalService;

  @InjectMocks private ThreatArsenalApi threatArsenalApi;

  @Test
  @DisplayName("Should return all argument types")
  void shouldReturnAllTypes() {

    List<PrimitiveType> types = threatArsenalApi.getArgumentTypes();

    assertThat(types).containsExactlyElementsOf(ChainingTypeRegistry.getPrimitiveTypes());
    assertThat(types).contains(PrimitiveType.AssetId, PrimitiveType.AssetGroupId);
  }
}
