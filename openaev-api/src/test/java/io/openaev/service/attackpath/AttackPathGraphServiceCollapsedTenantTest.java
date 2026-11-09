package io.openaev.service.attackpath;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.openaev.database.model.attackpath.projection.AttackPathInjectorMetaRow;
import io.openaev.database.repository.attackpath.AttackPathExecutionRepository;
import io.openaev.service.attackpath.dto.AttackPathNodeDTO;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Collapsed graph injector metadata tenant")
class AttackPathGraphServiceCollapsedTenantTest {

  private static final String SIMULATION_ID = "sim-collapsed-tenant";

  @Mock private AttackPathExecutionRepository executionRepository;
  @InjectMocks private AttackPathGraphService service;

  private static AttackPathInjectorMetaRow row(String contractExternalId, String tenantId) {
    return new AttackPathInjectorMetaRow("nmap", contractExternalId, "NMAP", tenantId);
  }

  @Nested
  @DisplayName("given the metadata rows of one simulation")
  class GivenTheMetadataRows {

    @Test
    @DisplayName("given_rowsOfTwoTenants_should_refuseNamingTheSimulation")
    void given_rowsOfTwoTenants_should_refuseNamingTheSimulation() {
      // Arrange
      when(executionRepository.findInjectorMetadata(SIMULATION_ID))
          .thenReturn(List.of(row("contract-a", "tenant-a"), row("contract-b", "tenant-b")));
      Map<String, AttackPathNodeDTO> nodes = new LinkedHashMap<>();

      // Act / Assert
      assertThatThrownBy(() -> service.enrichCollapsedInjectors(SIMULATION_ID, nodes))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining(SIMULATION_ID);
    }

    @Test
    @DisplayName("given_rowsOfOneTenant_should_enrichWithoutRefusing")
    void given_rowsOfOneTenant_should_enrichWithoutRefusing() {
      // Arrange
      when(executionRepository.findInjectorMetadata(SIMULATION_ID))
          .thenReturn(List.of(row(null, "tenant-a"), row(null, "tenant-a")));
      Map<String, AttackPathNodeDTO> nodes = new LinkedHashMap<>();

      // Act / Assert
      assertThatCode(() -> service.enrichCollapsedInjectors(SIMULATION_ID, nodes))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("given_noRow_should_enrichWithoutRefusing")
    void given_noRow_should_enrichWithoutRefusing() {
      // Arrange
      when(executionRepository.findInjectorMetadata(SIMULATION_ID)).thenReturn(List.of());
      Map<String, AttackPathNodeDTO> nodes = new LinkedHashMap<>();

      // Act / Assert
      assertThatCode(() -> service.enrichCollapsedInjectors(SIMULATION_ID, nodes))
          .doesNotThrowAnyException();
    }
  }
}
