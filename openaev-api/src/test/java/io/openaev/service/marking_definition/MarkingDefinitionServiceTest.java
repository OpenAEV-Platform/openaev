package io.openaev.service.marking_definition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.openaev.api.marking_definition.form.MarkingDefinitionInput;
import io.openaev.context.TxCtx;
import io.openaev.database.model.MarkingDefinition;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.MarkingDefinitionRepository;
import io.openaev.telemetry.metric_collectors.ActionMetricCollector;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("MarkingDefinitionService Tests")
class MarkingDefinitionServiceTest {

  @Mock private MarkingDefinitionRepository repository;
  @Mock private ActionMetricCollector actionMetricCollector;

  @InjectMocks private MarkingDefinitionService service;

  @Nested
  @DisplayName("Create")
  class Create {

    @Test
    @DisplayName("given_validInput_should_incrementMarkingDefinitionCreatedCount")
    void given_validInput_should_incrementMarkingDefinitionCreatedCount() {
      // Arrange
      String tenantId = "tenant-1";
      MarkingDefinitionInput input = new MarkingDefinitionInput("TLP", "TLP:RED", "#FF0000", 1);
      when(repository.existsByTypeAndDefinitionAndTenantIdExcludingId(
              "TLP", "TLP:RED", tenantId, null))
          .thenReturn(false);
      when(repository.save(any(MarkingDefinition.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      // Act
      service.create(input, tenantId);

      // Assert
      verify(actionMetricCollector, times(1)).addMarkingDefinitionCreatedCount();
      verify(actionMetricCollector, never()).addMarkingDefinitionUpdatedCount();
    }
  }

  @Nested
  @DisplayName("Update")
  class Update {

    @Test
    @DisplayName("given_validInput_should_incrementMarkingDefinitionUpdatedCount")
    void given_validInput_should_incrementMarkingDefinitionUpdatedCount() {
      // Arrange
      String tenantId = "tenant-1";
      String markingDefinitionId = "marking-1";
      Tenant tenant = new Tenant(tenantId);
      MarkingDefinition existing = new MarkingDefinition();
      existing.setId(markingDefinitionId);
      existing.setType("TLP");
      existing.setDefinition("TLP:AMBER");
      existing.setColor("#FFC107");
      existing.setOrder(1);
      existing.setProtectedDefinition(false);
      existing.setTenant(tenant);

      TxCtx ctx = TxCtx.forTenant(tenantId);
      MarkingDefinitionInput input = new MarkingDefinitionInput("TLP", "TLP:GREEN", "#4CAF50", 2);

      when(repository.findById(markingDefinitionId)).thenReturn(Optional.of(existing));
      when(repository.existsByTypeAndDefinitionAndTenantIdExcludingId(
              "TLP", "TLP:GREEN", tenantId, markingDefinitionId))
          .thenReturn(false);
      when(repository.save(any(MarkingDefinition.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      // Act
      MarkingDefinition updated = service.update(ctx, markingDefinitionId, input);

      // Assert
      assertThat(updated.getDefinition()).isEqualTo("TLP:GREEN");
      verify(actionMetricCollector, times(1)).addMarkingDefinitionUpdatedCount();
      verify(actionMetricCollector, never()).addMarkingDefinitionCreatedCount();
    }
  }
}
