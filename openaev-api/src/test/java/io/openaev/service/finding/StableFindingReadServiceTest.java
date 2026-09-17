package io.openaev.service.finding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.api.finding.StableFindingMapper;
import io.openaev.api.finding.StableFindingOutput;
import io.openaev.context.TxCtx;
import io.openaev.database.model.FindingLocationType;
import io.openaev.database.model.FindingOccurrence;
import io.openaev.database.model.StableFinding;
import io.openaev.database.repository.FindingOccurrenceRepository;
import io.openaev.database.repository.StableFindingRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Stable finding read service")
class StableFindingReadServiceTest {

  private static final String TENANT_ID = "tenant-a";
  private static final String STABLE_ID = "stable-id";

  @Mock private StableFindingRepository stableFindingRepository;
  @Mock private FindingOccurrenceRepository findingOccurrenceRepository;
  @Mock private StableFindingMapper stableFindingMapper;
  @Mock private StableFindingOutput stableFindingOutput;

  @InjectMocks private StableFindingReadService service;

  @Nested
  @DisplayName("Tenant-scoped lookup")
  class TenantScopedLookup {

    @Test
    @DisplayName("Includes authorized tenant ids in every by-id query")
    void given_authorizedTenant_should_useTenantScopedRepositories() {
      // Arrange
      StableFinding finding = new StableFinding();
      finding.setId(STABLE_ID);
      TxCtx ctx = TxCtx.forTenant(TENANT_ID);
      when(stableFindingRepository.findByIdAndTenantIdIn(STABLE_ID, Set.of(TENANT_ID)))
          .thenReturn(Optional.of(finding));
      when(findingOccurrenceRepository
              .findAllByStableFindingIdInAndTenantIdInOrderByObservedAtDescIdDesc(
                  List.of(STABLE_ID), Set.of(TENANT_ID)))
          .thenReturn(List.of());
      when(stableFindingMapper.toOutput(finding, List.of())).thenReturn(stableFindingOutput);

      // Act
      StableFindingOutput output = service.findById(ctx, STABLE_ID);

      // Assert
      assertThat(output).isSameAs(stableFindingOutput);
      verify(stableFindingRepository).findByIdAndTenantIdIn(STABLE_ID, Set.of(TENANT_ID));
      verify(findingOccurrenceRepository)
          .findAllByStableFindingIdInAndTenantIdInOrderByObservedAtDescIdDesc(
              List.of(STABLE_ID), Set.of(TENANT_ID));
    }

    @Test
    @DisplayName("Fails closed when the id is outside the authorized tenant scope")
    void given_outOfScopeId_should_throwNotFound() {
      // Arrange
      TxCtx ctx = TxCtx.forTenant(TENANT_ID);
      when(stableFindingRepository.findByIdAndTenantIdIn(STABLE_ID, Set.of(TENANT_ID)))
          .thenReturn(Optional.empty());

      // Act / Assert
      assertThatThrownBy(() -> service.findById(ctx, STABLE_ID))
          .isInstanceOf(EntityNotFoundException.class)
          .hasMessageContaining(STABLE_ID);
    }

    @Test
    @DisplayName("Groups authorized occurrences by location")
    void given_occurrences_should_groupLocationsWithinStableFinding() {
      // Arrange
      StableFinding finding = new StableFinding();
      finding.setId(STABLE_ID);
      FindingOccurrence older = occurrence("bucket-a", "2026-09-15T09:00:00Z", "High");
      FindingOccurrence latest = occurrence("bucket-a", "2026-09-17T09:00:00Z", "Critical");
      FindingOccurrence other = occurrence("bucket-b", "2026-09-16T09:00:00Z", "Medium");
      when(stableFindingRepository.findByIdAndTenantIdIn(STABLE_ID, Set.of(TENANT_ID)))
          .thenReturn(Optional.of(finding));
      when(findingOccurrenceRepository
              .findAllByStableFindingIdInAndTenantIdInOrderByObservedAtDescIdDesc(
                  List.of(STABLE_ID), Set.of(TENANT_ID)))
          .thenReturn(List.of(latest, other, older));

      // Act
      var locations = service.locations(TxCtx.forTenant(TENANT_ID), STABLE_ID);

      // Assert
      assertThat(locations).hasSize(2);
      assertThat(locations.getFirst().locationKey()).isEqualTo("bucket-a");
      assertThat(locations.getFirst().occurrences()).isEqualTo(2);
      assertThat(locations.getFirst().firstSeen())
          .isEqualTo(java.time.Instant.parse("2026-09-15T09:00:00Z"));
      assertThat(locations.getFirst().lastSeen())
          .isEqualTo(java.time.Instant.parse("2026-09-17T09:00:00Z"));
      assertThat(locations.getFirst().severity()).isEqualTo("Critical");
    }
  }

  private FindingOccurrence occurrence(String location, String observedAt, String severity) {
    FindingOccurrence occurrence = new FindingOccurrence();
    occurrence.setLocationType(FindingLocationType.RESOURCE);
    occurrence.setLocationKey(location);
    occurrence.setLocation(location);
    occurrence.setObservedAt(java.time.Instant.parse(observedAt));
    occurrence.setObservedSeverity(severity);
    return occurrence;
  }
}
