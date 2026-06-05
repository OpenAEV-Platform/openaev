package io.openaev.service.connector_instances;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorType;
import io.openaev.database.repository.ConnectorInstanceConfigurationRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConnectorInstanceServiceTest {

  private static final String TENANT_ID = "test-tenant-id";

  @Mock private ConnectorInstanceConfigurationRepository connectorInstanceConfigurationRepository;

  @InjectMocks private ConnectorInstanceService connectorInstanceService;

  @Nested
  @DisplayName("Check whether a started connector instance exists for a given injector")
  class HasStartedConnectorInstanceForInjector {

    @Test
    void given_startedStatusInDatabase_should_returnTrueWithoutFallbackRequest() {
      // Arrange
      String injectorId = "injector-1";
      when(connectorInstanceConfigurationRepository.findStatusByKeyValue(
              ConnectorType.INJECTOR.getIdKeyName(), injectorId, TENANT_ID))
          .thenReturn(Optional.of(ConnectorInstance.CURRENT_STATUS_TYPE.started.name()));

      // Act
      boolean result =
          connectorInstanceService.hasStartedConnectorInstanceForInjector(injectorId, TENANT_ID);

      // Assert
      assertTrue(result);
    }

    @Test
    void given_nonStartedStatusInDatabase_should_returnFalseWithoutFallbackRequest() {
      // Arrange
      String injectorId = "injector-2";
      when(connectorInstanceConfigurationRepository.findStatusByKeyValue(
              ConnectorType.INJECTOR.getIdKeyName(), injectorId, TENANT_ID))
          .thenReturn(Optional.of(ConnectorInstance.CURRENT_STATUS_TYPE.stopped.name()));

      // Act
      boolean result =
          connectorInstanceService.hasStartedConnectorInstanceForInjector(injectorId, TENANT_ID);

      // Assert
      assertFalse(result);
    }

    @Test
    void given_missingStatusInDatabase_should_returnTrue() {
      // Arrange
      String injectorId = "injector-3";
      when(connectorInstanceConfigurationRepository.findStatusByKeyValue(
              ConnectorType.INJECTOR.getIdKeyName(), injectorId, TENANT_ID))
          .thenReturn(Optional.empty());

      // Act
      boolean result =
          connectorInstanceService.hasStartedConnectorInstanceForInjector(injectorId, TENANT_ID);

      // Assert
      assertTrue(result);
    }
  }
}
