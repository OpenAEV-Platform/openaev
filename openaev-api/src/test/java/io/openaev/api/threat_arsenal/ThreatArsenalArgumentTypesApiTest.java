package io.openaev.api.threat_arsenal;

import static io.openaev.rest.settings.PreviewFeature.INJECT_CHAINING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.openaev.database.model.PrimitiveType;
import io.openaev.service.PreviewFeatureService;
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
  @Mock private PreviewFeatureService previewFeatureService;

  @InjectMocks private ThreatArsenalApi threatArsenalApi;

  @Test
  @DisplayName("Should return text/document/targeted-asset when chaining is disabled")
  void given_chainingDisabled_should_returnCoreTypesOnly() {
    when(previewFeatureService.isFeatureEnabled(INJECT_CHAINING)).thenReturn(false);

    List<PrimitiveType> types = threatArsenalApi.getArgumentTypes();

    assertThat(types)
        .containsExactly(PrimitiveType.Text, PrimitiveType.Document, PrimitiveType.TargetedAsset);
  }

  @Test
  @DisplayName("Should return all argument types when chaining is enabled")
  void given_chainingEnabled_should_returnAllTypes() {
    when(previewFeatureService.isFeatureEnabled(INJECT_CHAINING)).thenReturn(true);

    List<PrimitiveType> types = threatArsenalApi.getArgumentTypes();

    assertThat(types).containsExactly(PrimitiveType.values());
    assertThat(types).contains(PrimitiveType.AssetId, PrimitiveType.AssetGroupId);
  }
}
