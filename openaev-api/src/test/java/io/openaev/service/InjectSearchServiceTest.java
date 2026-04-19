package io.openaev.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.openaev.database.model.ExecutionStatus;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectStatus;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.repository.AssetGroupRepository;
import io.openaev.database.repository.AssetRepository;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.database.repository.TeamRepository;
import io.openaev.healthcheck.utils.HealthCheckUtils;
import io.openaev.rest.atomic_testing.form.InjectResultOutput;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectStatusFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.mapper.InjectExpectationMapper;
import io.openaev.utils.mapper.InjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaQuery;
import java.time.Instant;
import java.util.*;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InjectSearchServiceTest {

  @Mock private InjectExpectationRepository injectExpectationRepository;
  @Mock private TeamRepository teamRepository;
  @Mock private AssetRepository assetRepository;
  @Mock private AssetGroupRepository assetGroupRepository;
  @Mock private InjectMapper injectMapper;
  @Mock private InjectExpectationMapper injectExpectationMapper;
  @Mock private HealthCheckUtils healthCheckUtils;
  @Mock private EntityManager entityManager;

  @Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS)
  private HibernateCriteriaBuilder criteriaBuilder;

  @InjectMocks private InjectSearchService injectSearchService;

  @BeforeEach
  void injectEntityManager() {
    ReflectionTestUtils.setField(injectSearchService, "entityManager", entityManager);
  }

  @Nested
  @DisplayName("FetchRelatedTargets")
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

  @Nested
  @DisplayName("GetListOfInjectResults")
  class GetListOfInjectResults {

    @BeforeEach
    void setUpCriteriaBuilder() {
      when(entityManager.getCriteriaBuilder()).thenReturn(criteriaBuilder);
    }

    @Test
    @DisplayName("given_exerciseWithInjectAndStatus_should_returnInjectResultWithCorrectFields")
    void given_exerciseWithInjectAndStatus_should_returnInjectResultWithCorrectFields() {
      // -------- Arrange --------
      InjectorContract contract = InjectorContractFixture.createDefaultInjectorContract();
      Inject inject = InjectFixture.getDefaultInject();
      inject.setId(UUID.randomUUID().toString());
      inject.setInjectorContract(contract);
      InjectStatus status = InjectStatusFixture.createSuccessStatus();
      status.setId(UUID.randomUUID().toString());

      Tuple tuple = createInjectResultTuple(inject, contract, status);
      setupQueryToReturn(List.of(tuple));
      when(injectMapper.toTargetSimple(anyList(), any())).thenReturn(List.of());

      // -------- Act --------
      List<InjectResultOutput> results =
          injectSearchService.getListOfInjectResults(UUID.randomUUID().toString());

      // -------- Assert --------
      assertThat(results).hasSize(1);
      InjectResultOutput result = results.get(0);
      assertThat(result.getId()).isEqualTo(inject.getId());
      assertThat(result.getTitle()).isEqualTo(inject.getTitle());
      assertThat(result.getUpdatedAt()).isNotNull();
      assertThat(result.getInjectType()).isEqualTo(contract.getInjectorType());
      assertThat(result.getInjectorContract()).isNotNull();
      assertThat(result.getInjectorContract().getId()).isEqualTo(contract.getId());
      assertThat(result.getStatus()).isNotNull();
      assertThat(result.getStatus().getName()).isEqualTo(ExecutionStatus.SUCCESS.name());
    }

    @Test
    @DisplayName("given_noInjects_should_returnEmptyList")
    void given_noInjects_should_returnEmptyList() {
      // -------- Arrange --------
      setupQueryToReturn(List.of());

      // -------- Act --------
      List<InjectResultOutput> results =
          injectSearchService.getListOfInjectResults(UUID.randomUUID().toString());

      // -------- Assert --------
      assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("given_multipleInjects_should_returnAllResults")
    void given_multipleInjects_should_returnAllResults() {
      // -------- Arrange --------
      InjectorContract contract1 = InjectorContractFixture.createDefaultInjectorContract();
      Inject inject1 = InjectFixture.getDefaultInject();
      inject1.setId(UUID.randomUUID().toString());
      inject1.setInjectorContract(contract1);

      InjectorContract contract2 = InjectorContractFixture.createDefaultInjectorContract();
      Inject inject2 = InjectFixture.getDefaultInject();
      inject2.setId(UUID.randomUUID().toString());
      inject2.setInjectorContract(contract2);

      Tuple tuple1 = createInjectResultTuple(inject1, contract1, null);
      Tuple tuple2 = createInjectResultTuple(inject2, contract2, null);
      setupQueryToReturn(List.of(tuple1, tuple2));
      when(injectMapper.toTargetSimple(anyList(), any())).thenReturn(List.of());

      // -------- Act --------
      List<InjectResultOutput> results =
          injectSearchService.getListOfInjectResults(UUID.randomUUID().toString());

      // -------- Assert --------
      assertThat(results).hasSize(2);
      assertThat(results)
          .extracting(InjectResultOutput::getId)
          .containsExactlyInAnyOrder(inject1.getId(), inject2.getId());
    }

    @Test
    @DisplayName("given_injectWithoutContract_should_returnResultWithNullContract")
    void given_injectWithoutContract_should_returnResultWithNullContract() {
      // -------- Arrange --------
      Inject inject = InjectFixture.getDefaultInject();
      inject.setId(UUID.randomUUID().toString());

      Tuple tuple = createInjectResultTuple(inject, null, null);
      setupQueryToReturn(List.of(tuple));

      // -------- Act --------
      List<InjectResultOutput> results =
          injectSearchService.getListOfInjectResults(UUID.randomUUID().toString());

      // -------- Assert --------
      assertThat(results).hasSize(1);
      assertThat(results.get(0).getInjectorContract()).isNull();
      assertThat(results.get(0).getInjectType()).isNull();
      assertThat(results.get(0).getStatus()).isNotNull();
    }

    // -- helpers --
    private void setupQueryToReturn(List<Tuple> tuples) {
      TypedQuery<Tuple> mockQuery = mock(TypedQuery.class);
      when(entityManager.createQuery(any(CriteriaQuery.class))).thenReturn(mockQuery);
      when(mockQuery.setFirstResult(anyInt())).thenReturn(mockQuery);
      when(mockQuery.setMaxResults(anyInt())).thenReturn(mockQuery);
      when(mockQuery.getResultList()).thenReturn(tuples);
    }

    private Tuple createInjectResultTuple(
        Inject inject, InjectorContract contract, InjectStatus status) {
      Map<String, Object> data = new HashMap<>();
      data.put("inject_id", inject.getId());
      data.put("inject_title", inject.getTitle());
      data.put("inject_updated_at", Instant.now());
      data.put("inject_content", inject.getContent());

      if (contract != null) {
        data.put("inject_type", contract.getInjectorType());
        data.put("injector_contract_id", contract.getId());
        data.put("injector_contract_content", contract.getContent());
        data.put("convertedContent", contract.getConvertedContent());
        data.put("injector_contract_platforms", contract.getPlatforms());
        data.put("injector_contract_labels", contract.getLabels());
      }

      if (status != null) {
        data.put("status_name", status.getName());
        data.put("status_id", status.getId());
        data.put("status_tracking_sent_date", status.getTrackingSentDate());
      }

      Tuple tuple = mock(Tuple.class);
      when(tuple.get(anyString(), any()))
          .thenAnswer(invocation -> data.get(invocation.getArgument(0)));
      return tuple;
    }
  }
}
