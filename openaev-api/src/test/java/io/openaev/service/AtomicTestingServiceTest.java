package io.openaev.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.openaev.context.TenantContext;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.Inject;
import io.openaev.database.repository.InjectRepository;
import io.openaev.rest.atomic_testing.form.InjectResultOverviewOutput;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.service.utils.BulkDeleteExecutor;
import io.openaev.telemetry.metric_collectors.ActionMetricCollector;
import io.openaev.utils.InjectUtils;
import io.openaev.utils.injector_contract.InjectorContractContentUtils;
import io.openaev.utils.mapper.InjectMapper;
import io.openaev.utils.mapper.PayloadMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Regression coverage for {@link AtomicTestingService#findById(String)}: a GET-triggered read must
 * never mutate the managed {@code inject.assetGroups} {@code @ManyToMany} collection. Doing so (the
 * previous {@code clear()}/{@code addAll()} implementation) churns the {@code injects_asset_groups}
 * join table on every single read, and under concurrent requests for the same inject the resulting
 * delete-then-reinsert races and can violate the join table's primary key ({@code
 * ConstraintViolationException} on {@code injects_asset_groups}).
 */
@ExtendWith(MockitoExtension.class)
class AtomicTestingServiceTest {

  @Mock private InjectMapper injectMapper;
  @Mock private ActionMetricCollector actionMetricCollector;
  @Mock private io.openaev.database.repository.AssetGroupRepository assetGroupRepository;
  @Mock private io.openaev.database.repository.AssetRepository assetRepository;
  @Mock private PayloadMapper payloadMapper;
  @Mock private InjectRepository injectRepository;

  @Mock
  private io.openaev.database.repository.InjectorContractRepository injectorContractRepository;

  @Mock private io.openaev.database.repository.UserRepository userRepository;
  @Mock private io.openaev.database.repository.TeamRepository teamRepository;
  @Mock private io.openaev.database.repository.TagRepository tagRepository;
  @Mock private io.openaev.database.repository.DocumentRepository documentRepository;
  @Mock private AssetGroupService assetGroupService;
  @Mock private InjectExpectationService injectExpectationService;
  @Mock private UserService userService;
  @Mock private InjectSearchService injectSearchService;
  @Mock private InjectService injectService;
  @Mock private GrantService grantService;
  @Mock private io.openaev.database.repository.InjectDocumentRepository injectDocumentRepository;
  @Mock private InjectUtils injectUtils;
  @Mock private InjectorContractContentUtils injectorContractContentUtils;
  @Mock private BulkDeleteExecutor bulkDeleteExecutor;

  @InjectMocks private AtomicTestingService atomicTestingService;

  private static final String INJECT_ID = "inject-001";
  private static final String TENANT_ID = "tenant-001";

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenant(TENANT_ID);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("findById - must not mutate the managed assetGroups collection")
  class FindById {

    @Test
    @DisplayName(
        "Given an inject with asset groups, findById should compute dynamic assets in place without clearing or replacing the collection")
    void given_injectWithAssetGroups_should_computeDynamicAssetsWithoutTouchingCollection() {
      // -------- Arrange --------
      AssetGroup assetGroup = new AssetGroup();
      assetGroup.setId("asset-group-001");
      List<AssetGroup> assetGroups = spy(new ArrayList<>(List.of(assetGroup)));

      Inject inject = new Inject();
      inject.setAssetGroups(assetGroups);

      when(injectRepository.findByIdAndTenantId(INJECT_ID, TENANT_ID))
          .thenReturn(Optional.of(inject));
      when(injectMapper.toInjectResultOverviewOutput(inject))
          .thenReturn(mock(InjectResultOverviewOutput.class));
      when(assetGroupService.computeDynamicAssets(assetGroup)).thenReturn(assetGroup);

      // -------- Act --------
      atomicTestingService.findById(INJECT_ID);

      // -------- Assert --------
      verify(assetGroupService).computeDynamicAssets(assetGroup);
      // The join-table-backed collection must never be structurally modified by a read
      verify(assetGroups, never()).clear();
      verify(assetGroups, never()).addAll(any());
      assertThat(inject.getAssetGroups())
          .as("The exact same collection instance must be returned, untouched")
          .isSameAs(assetGroups)
          .containsExactly(assetGroup);
    }

    @Test
    @DisplayName(
        "Given an inject without asset groups, findById should not interact with assetGroupService")
    void given_injectWithoutAssetGroups_should_notInteractWithAssetGroupService() {
      // -------- Arrange --------
      Inject inject = new Inject();

      when(injectRepository.findByIdAndTenantId(INJECT_ID, TENANT_ID))
          .thenReturn(Optional.of(inject));
      when(injectMapper.toInjectResultOverviewOutput(inject))
          .thenReturn(mock(InjectResultOverviewOutput.class));

      // -------- Act --------
      atomicTestingService.findById(INJECT_ID);

      // -------- Assert --------
      verifyNoInteractions(assetGroupService);
    }
  }
}
