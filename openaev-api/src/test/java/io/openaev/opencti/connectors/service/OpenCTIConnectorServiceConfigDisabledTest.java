package io.openaev.opencti.connectors.service;

import static org.assertj.core.api.AssertionsForClassTypes.*;
import static org.mockito.Mockito.*;

import io.openaev.IntegrationTest;
import io.openaev.opencti.client.OpenCTIClient;
import io.openaev.opencti.errors.ConnectorError;
import io.openaev.stix.objects.Bundle;
import io.openaev.stix.types.Identifier;
import io.openaev.utils.mockConfig.WithMockSecurityCoverageConnectorConfig;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@WithMockSecurityCoverageConnectorConfig // null config
public class OpenCTIConnectorServiceConfigDisabledTest extends IntegrationTest {

  @MockBean private OpenCTIClient mockOpenCTIClient;
  @Autowired OpenCTIConnectorService openCTIConnectorService;

  @BeforeEach
  public void setup() {
    reset(mockOpenCTIClient);
  }

  @Nested
  @DisplayName("Push STIX bundle tests")
  public class PushSTIXBundleTests {

    private Bundle createBundle() {
      return new Bundle(new Identifier("titi"), List.of());
    }

    @Nested
    @DisplayName("When connector is NOT configured")
    public class WhenConnectorIsNOTConfigured {

      @Test
      @DisplayName("throw exception")
      public void whenConnectorIsNotRegistered_throwException() {
        assertThatThrownBy(
                () -> openCTIConnectorService.pushSecurityCoverageStixBundle(createBundle()))
            .isInstanceOf(ConnectorError.class)
            .hasMessage(
                "No instance of Security Coverage connector is currently active to send security coverage bundles.");
      }
    }
  }
}
