package io.openaev.notification.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.config.cache.MarkingClearanceCacheManager;
import io.openaev.context.MarkingCtx;
import io.openaev.database.model.Asset;
import io.openaev.database.model.NotificationTriggerEventType;
import io.openaev.database.model.ResourceType;
import io.openaev.database.repository.AssetRepository;
import io.openaev.rest.finding.FindingService;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.PreviewFeatureService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Notification marking resolver")
class NotificationMarkingResolverTest {

  private static final String TENANT_A = "tenant-a";

  private AssetRepository assetRepository;
  private FindingService findingService;
  private InjectService injectService;
  private MarkingClearanceCacheManager markingClearanceCacheManager;
  private PreviewFeatureService previewFeatureService;
  private NotificationMarkingResolver resolver;

  @BeforeEach
  void setUp() {
    assetRepository = mock(AssetRepository.class);
    findingService = mock(FindingService.class);
    injectService = mock(InjectService.class);
    markingClearanceCacheManager = mock(MarkingClearanceCacheManager.class);
    previewFeatureService = mock(PreviewFeatureService.class);
    resolver =
        new NotificationMarkingResolver(
            assetRepository,
            findingService,
            injectService,
            markingClearanceCacheManager,
            previewFeatureService);
  }

  @Nested
  @DisplayName("resolveMarkingSets")
  class ResolveMarkingSets {

    @Test
    @DisplayName("given a DELETE event, should resolve to no markings without querying anything")
    void given_deleteEvent_should_resolveEmptyWithoutQuerying() {
      List<String[]> result =
          resolver.resolveMarkingSets(
              ResourceType.ASSET, "asset-id", TENANT_A, NotificationTriggerEventType.DELETE);

      assertTrue(result.isEmpty());
      verify(assetRepository, never()).findByIdAndTenantId(anyString(), anyString());
    }

    @Test
    @DisplayName("given an asset with its own markings, should resolve a singleton marking set")
    void given_markedAsset_should_resolveSingletonSet() {
      Asset asset = new Asset();
      asset.setMarkingIds(new String[] {"tlp-red"});
      when(assetRepository.findByIdAndTenantId("asset-id", TENANT_A))
          .thenReturn(Optional.of(asset));

      List<String[]> result =
          resolver.resolveMarkingSets(
              ResourceType.ASSET, "asset-id", TENANT_A, NotificationTriggerEventType.CREATE);

      assertEquals(1, result.size());
      assertEquals(List.of("tlp-red"), List.of(result.getFirst()));
    }

    @Test
    @DisplayName("given an unmarked asset, should resolve to no markings")
    void given_unmarkedAsset_should_resolveEmpty() {
      Asset asset = new Asset();
      asset.setMarkingIds(null);
      when(assetRepository.findByIdAndTenantId("asset-id", TENANT_A))
          .thenReturn(Optional.of(asset));

      List<String[]> result =
          resolver.resolveMarkingSets(
              ResourceType.ASSET, "asset-id", TENANT_A, NotificationTriggerEventType.CREATE);

      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("given an asset not found for the tenant, should resolve to no markings")
    void given_assetNotFoundForTenant_should_resolveEmpty() {
      when(assetRepository.findByIdAndTenantId("asset-id", TENANT_A)).thenReturn(Optional.empty());

      List<String[]> result =
          resolver.resolveMarkingSets(
              ResourceType.ASSET, "asset-id", TENANT_A, NotificationTriggerEventType.CREATE);

      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("given a finding, should delegate to FindingService's linked-asset markings")
    void given_finding_should_delegateToFindingService() {
      List<String[]> markingSets = List.<String[]>of(new String[] {"tlp-red"});
      when(findingService.findAssetMarkingIds("finding-id")).thenReturn(markingSets);

      List<String[]> result =
          resolver.resolveMarkingSets(
              ResourceType.FINDING, "finding-id", TENANT_A, NotificationTriggerEventType.CREATE);

      assertEquals(markingSets, result);
    }

    @Test
    @DisplayName("given an inject, should delegate to InjectService's reachable-asset markings")
    void given_inject_should_delegateToInjectService() {
      List<String[]> markingSets = List.<String[]>of(new String[] {"tlp-red"});
      when(injectService.findAssetMarkingIds("inject-id")).thenReturn(markingSets);

      List<String[]> result =
          resolver.resolveMarkingSets(
              ResourceType.INJECT, "inject-id", TENANT_A, NotificationTriggerEventType.CREATE);

      assertEquals(markingSets, result);
    }

    @Test
    @DisplayName("given a resource type with no markings concept, should resolve to no markings")
    void given_unmarkableResourceType_should_resolveEmpty() {
      List<String[]> result =
          resolver.resolveMarkingSets(
              ResourceType.SCENARIO, "scenario-id", TENANT_A, NotificationTriggerEventType.CREATE);

      assertTrue(result.isEmpty());
    }
  }

  @Nested
  @DisplayName("isVisibleTo")
  class IsVisibleTo {

    @Test
    @DisplayName(
        "given the MARKING preview feature is off, should be visible regardless of clearance")
    void given_markingFeatureOff_should_beVisible() {
      boolean visible =
          resolver.isVisibleTo(
              "user-1",
              TENANT_A,
              ResourceType.ASSET,
              "asset-id",
              NotificationTriggerEventType.CREATE);

      assertTrue(visible);
      verify(assetRepository, never()).findByIdAndTenantId(anyString(), anyString());
    }

    @Test
    @DisplayName("given no marking sets, should be visible to everyone")
    void given_noMarkingSets_should_beVisible() {
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.MARKING)).thenReturn(true);
      when(assetRepository.findByIdAndTenantId("asset-id", TENANT_A)).thenReturn(Optional.empty());

      boolean visible =
          resolver.isVisibleTo(
              "user-1",
              TENANT_A,
              ResourceType.ASSET,
              "asset-id",
              NotificationTriggerEventType.CREATE);

      assertTrue(visible);
    }

    @Test
    @DisplayName("given clearance covers one of several marking sets, should be visible (OR)")
    void given_clearanceCoversOneSet_should_beVisible() {
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.MARKING)).thenReturn(true);
      when(findingService.findAssetMarkingIds("finding-id"))
          .thenReturn(List.of(new String[] {"tlp-red"}, new String[] {"tlp-amber"}));
      when(markingClearanceCacheManager.findClearance("user-1", TENANT_A))
          .thenReturn(MarkingCtx.forMarkings(List.of("tlp-amber")));

      boolean visible =
          resolver.isVisibleTo(
              "user-1",
              TENANT_A,
              ResourceType.FINDING,
              "finding-id",
              NotificationTriggerEventType.CREATE);

      assertTrue(visible);
    }

    @Test
    @DisplayName("given clearance covers none of the marking sets, should not be visible")
    void given_clearanceCoversNoSet_should_beNotVisible() {
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.MARKING)).thenReturn(true);
      when(injectService.findAssetMarkingIds("inject-id"))
          .thenReturn(List.<String[]>of(new String[] {"tlp-red"}));
      when(markingClearanceCacheManager.findClearance("user-1", TENANT_A))
          .thenReturn(MarkingCtx.none());

      boolean visible =
          resolver.isVisibleTo(
              "user-1",
              TENANT_A,
              ResourceType.INJECT,
              "inject-id",
              NotificationTriggerEventType.CREATE);

      assertFalse(visible);
    }
  }
}
