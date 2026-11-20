package io.openaev.service.payload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.openaev.database.model.Payload;
import io.openaev.database.repository.PayloadIdentityRepository;
import io.openaev.rest.payload.DetectionRemediationUtils;
import io.openaev.rest.payload.PayloadUtils;
import io.openaev.rest.payload.form.PayloadUpdateInput;
import io.openaev.rest.payload.service.PayloadService;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.PreviewFeatureService;
import io.openaev.utils.fixtures.PayloadFixture;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Payloads saved in place")
class PayloadLocalVersioningTest {

  @Mock private PayloadService payloadService;
  @Mock private PayloadUtils payloadUtils;
  @Mock private DetectionRemediationUtils detectionRemediationUtils;
  @Mock private PayloadChangeKind payloadChangeKind;
  @Mock private PayloadVersionMatcher payloadVersionMatcher;
  @Mock private PayloadIdentityRepository payloadIdentityRepository;
  @Mock private PreviewFeatureService previewFeatureService;
  @InjectMocks private PayloadLocalVersioning payloadLocalVersioning;

  @Test
  void given_previewOff_should_updateInPlace() {
    // Arrange
    Payload local = PayloadFixture.createDefaultCommand();
    when(previewFeatureService.isFeatureEnabled(PreviewFeature.CATALOG)).thenReturn(false);

    // Act & Assert
    assertThat(payloadLocalVersioning.newVersion(local, new PayloadUpdateInput(), List.of()))
        .isEmpty();
    verifyNoInteractions(payloadService, payloadChangeKind);
  }

  @Test
  void given_cataloguePayload_should_updateInPlace() {
    // Arrange
    Payload catalogue = PayloadFixture.createDefaultCommand();
    catalogue.getIdentity().setRepository("filigran");

    // Act & Assert
    assertThat(payloadLocalVersioning.newVersion(catalogue, new PayloadUpdateInput(), List.of()))
        .isEmpty();
    verifyNoInteractions(payloadService, payloadChangeKind, previewFeatureService);
  }
}
