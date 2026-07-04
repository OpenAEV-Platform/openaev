package io.openaev.api.payload;

import static io.openaev.rest.settings.PreviewFeature.INJECT_CHAINING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.openaev.database.model.PrimitiveType;
import io.openaev.service.PreviewFeatureService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Payload argument type API")
class PayloadArgumentApiTest {

  @Mock private PreviewFeatureService previewFeatureService;

  @InjectMocks private PayloadArgumentApi payloadArgumentApi;

  @Test
  @DisplayName("Should return only text/document when chaining is disabled")
  void given_chainingDisabled_should_returnCoreTypesOnly() {
    // Arrange
    when(previewFeatureService.isFeatureEnabled(INJECT_CHAINING)).thenReturn(false);

    // Act
    List<PrimitiveTypeOutput> body = payloadArgumentApi.getArgumentTypes().getBody();
    assertThat(body).isNotNull();
    List<PrimitiveType> types = body.stream().map(PrimitiveTypeOutput::type).toList();

    // Assert
    assertThat(types).containsExactly(PrimitiveType.Text, PrimitiveType.Document);
  }

  @Test
  @DisplayName("Should return all argument types when chaining is enabled")
  void given_chainingEnabled_should_returnAllTypes() {
    // Arrange
    when(previewFeatureService.isFeatureEnabled(INJECT_CHAINING)).thenReturn(true);

    // Act
    List<PrimitiveTypeOutput> body = payloadArgumentApi.getArgumentTypes().getBody();
    assertThat(body).isNotNull();
    List<PrimitiveType> types = body.stream().map(PrimitiveTypeOutput::type).toList();

    // Assert
    assertThat(types).containsExactly(PrimitiveType.values());
    assertThat(types.stream().map(Enum::name).toList())
        .doesNotContain("Credentials", "PortsScan", "Vulnerability");
  }
}
