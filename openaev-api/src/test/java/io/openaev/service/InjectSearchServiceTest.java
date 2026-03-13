package io.openaev.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.openaev.database.repository.AssetGroupRepository;
import io.openaev.database.repository.AssetRepository;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.database.repository.TeamRepository;
import io.openaev.healthcheck.utils.HealthCheckUtils;
import io.openaev.utils.mapper.InjectExpectationMapper;
import io.openaev.utils.mapper.InjectMapper;
import java.util.*;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InjectSearchServiceTest {

  @Mock private InjectExpectationRepository injectExpectationRepository;
  @Mock private TeamRepository teamRepository;
  @Mock private AssetRepository assetRepository;
  @Mock private AssetGroupRepository assetGroupRepository;
  @Mock private InjectMapper injectMapper;
  @Mock private InjectExpectationMapper injectExpectationMapper;
  @Mock private HealthCheckUtils healthCheckUtils;

  @InjectMocks private InjectSearchService injectSearchService;

  @Nested
  class FetchRelatedTargets {

    @Test
    void shouldReturnTeams_whenTargetTypeIsTeams() {
      // -------- Prepare --------
      Set<String> injectIds = Set.of("inject-1");
      Object[] row = new Object[] {"inject-1", "team-1", "TeamName"};
      List<Object[]> rows = new ArrayList<>();
      rows.add(row);
      when(teamRepository.teamsByInjectIds(injectIds)).thenReturn(rows);

      // -------- Act --------
      Map<String, List<Object[]>> result =
          injectSearchService.fetchRelatedTargets(injectIds, "teams");

      // -------- Assert --------
      assertNotNull(result);
      assertTrue(result.containsKey("inject-1"));
      assertEquals(1, result.get("inject-1").size());
    }

    @Test
    void shouldReturnAssets_whenTargetTypeIsAssets() {
      // -------- Prepare --------
      Set<String> injectIds = Set.of("inject-1");
      Object[] row = new Object[] {"inject-1", "asset-1", "AssetName"};
      List<Object[]> rows = new ArrayList<>();
      rows.add(row);
      when(assetRepository.assetsByInjectIds(injectIds)).thenReturn(rows);

      // -------- Act --------
      Map<String, List<Object[]>> result =
          injectSearchService.fetchRelatedTargets(injectIds, "assets");

      // -------- Assert --------
      assertNotNull(result);
      assertTrue(result.containsKey("inject-1"));
    }

    @Test
    void shouldReturnAssetGroups_whenTargetTypeIsAssetGroups() {
      // -------- Prepare --------
      Set<String> injectIds = Set.of("inject-1");
      Object[] row = new Object[] {"inject-1", "ag-1", "GroupName"};
      List<Object[]> rows = new ArrayList<>();
      rows.add(row);
      when(assetGroupRepository.assetGroupsByInjectIds(injectIds)).thenReturn(rows);

      // -------- Act --------
      Map<String, List<Object[]>> result =
          injectSearchService.fetchRelatedTargets(injectIds, "assetGroups");

      // -------- Assert --------
      assertNotNull(result);
      assertTrue(result.containsKey("inject-1"));
    }

    @Test
    void shouldReturnEmptyMap_whenInjectIdsEmpty() {
      // -------- Prepare --------
      Set<String> emptyIds = Collections.emptySet();

      // -------- Act --------
      Map<String, List<Object[]>> result =
          injectSearchService.fetchRelatedTargets(emptyIds, "teams");

      // -------- Assert --------
      assertNotNull(result);
      assertTrue(result.isEmpty());
      verifyNoInteractions(teamRepository);
    }

    @Test
    void shouldReturnEmptyMap_whenInjectIdsNull() {
      // -------- Prepare --------
      // null inject IDs

      // -------- Act --------
      Map<String, List<Object[]>> result = injectSearchService.fetchRelatedTargets(null, "teams");

      // -------- Assert --------
      assertNotNull(result);
      assertTrue(result.isEmpty());
    }

    @Test
    void shouldThrowIllegalArgument_whenTargetTypeUnknown() {
      // -------- Prepare --------
      Set<String> injectIds = Set.of("inject-1");

      // -------- Act / Assert --------
      assertThrows(
          IllegalArgumentException.class,
          () -> injectSearchService.fetchRelatedTargets(injectIds, "unknown"));
    }
  }
}
