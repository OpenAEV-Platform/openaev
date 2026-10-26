package io.openaev.service.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.openaev.config.MarkedTables;
import io.openaev.context.MarkingScopeSupplier;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Grant;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.User;
import io.openaev.database.raw.RawGrant;
import io.openaev.database.raw.RawUserAuth;
import io.openaev.database.repository.UserRepository;
import io.openaev.engine.security.EngineReadScope;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.PermissionService;
import io.openaev.service.PreviewFeatureService;
import io.openaev.service.UserService;
import io.openaev.utils.mapper.RawUserAuthMapper;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * How the reader scope is resolved from the source of truth. Here markings are off ({@code assets}
 * not active); the marking lookup against real rows, clearances and tenants is covered by {@link
 * EngineReadScopeResolverIntegrationTest}. The {@code MARKING} preview feature is on unless a test
 * says otherwise.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Engine read scope resolution")
class EngineReadScopeResolverTest {

  private static final String TENANT_A = "tenant-a";
  private static final String TENANT_B = "tenant-b";
  private static final TxCtx CTX = TxCtx.forTenant(TENANT_A);

  @Mock private PermissionService permissionService;
  @Mock private UserService userService;
  @Mock private UserRepository userRepository;
  @Mock private RawUserAuthMapper rawUserAuthMapper;
  @Mock private MarkingScopeSupplier markingScopeSupplier;
  @Mock private JdbcTemplate jdbcTemplate;
  @Mock private PreviewFeatureService previewFeatureService;
  @Mock private User user;
  @Mock private RawUserAuth auth;

  private EngineReadScopeResolver resolver;

  @BeforeEach
  void setUp() {
    resolver =
        new EngineReadScopeResolver(
            permissionService,
            userService,
            userRepository,
            rawUserAuthMapper,
            markingScopeSupplier,
            MarkedTables.EMPTY,
            jdbcTemplate,
            previewFeatureService);
    lenient().when(previewFeatureService.isFeatureEnabled(PreviewFeature.MARKING)).thenReturn(true);
    lenient().when(userService.currentUser()).thenReturn(user);
    lenient().when(user.getId()).thenReturn("user-1");
    lenient().when(rawUserAuthMapper.toRawUserAuth(any())).thenReturn(auth);
    lenient().when(auth.getUser_grants()).thenReturn(Set.of());
  }

  private void grantCapabilities(ResourceType... granted) {
    Set<ResourceType> held = Set.of(granted);
    lenient()
        .when(permissionService.hasCapabilityPermissionInTenant(eq(user), any(), any(), any()))
        .thenAnswer(invocation -> held.contains(invocation.<ResourceType>getArgument(2)));
  }

  private EngineReadScope.TenantRules rulesOf(EngineReadScope scope) {
    return scope.tenantRules().get(TENANT_A);
  }

  private static RawGrant grant(String resourceId, Grant.GRANT_RESOURCE_TYPE type) {
    RawGrant grant = mock(RawGrant.class);
    when(grant.getGrant_resource()).thenReturn(resourceId);
    when(grant.getGrant_resource_type()).thenReturn(type);
    return grant;
  }

  @Test
  @DisplayName("given the MARKING preview feature off, should be unrestricted for every user")
  void given_markingFeatureOff_should_beUnrestrictedForEveryUser() {
    // -- ARRANGE --
    when(previewFeatureService.isFeatureEnabled(PreviewFeature.MARKING)).thenReturn(false);

    // -- ACT --
    EngineReadScope scope = resolver.resolve(CTX);

    // -- ASSERT --
    assertThat(scope).isEqualTo(EngineReadScope.unrestricted(null));
    verifyNoInteractions(userService, permissionService, jdbcTemplate);
  }

  @Test
  @DisplayName("given an admin, should be unrestricted")
  void given_admin_should_beUnrestricted() {
    // -- ARRANGE --
    when(user.isAdmin()).thenReturn(true);

    // -- ACT --
    EngineReadScope scope = resolver.resolve(CTX);

    // -- ASSERT --
    assertThat(scope.unrestricted()).isTrue();
  }

  @Nested
  @DisplayName("Capability")
  class Capability {

    @Test
    @DisplayName("given no ACCESS_ASSETS, should not read the asset index")
    void given_noAccessAssets_should_notReadAssets() {
      // -- ARRANGE --
      grantCapabilities(ResourceType.SIMULATION, ResourceType.SCENARIO, ResourceType.FINDING);

      // -- ACT --
      EngineReadScope scope = resolver.resolve(CTX);

      // -- ASSERT --
      assertThat(scope.unrestricted()).isFalse();
      assertThat(rulesOf(scope).readableEntities()).doesNotContain("asset", "vulnerable-endpoint");
      assertThat(rulesOf(scope).readableEntities()).contains("finding", "scenario", "tag");
    }

    @Test
    @DisplayName("given every capability and no marking, should be unrestricted")
    void given_everyCapability_should_beUnrestricted() {
      // -- ARRANGE --
      lenient()
          .when(permissionService.hasCapabilityPermissionInTenant(eq(user), any(), any(), any()))
          .thenReturn(true);

      // -- ACT --
      EngineReadScope scope = resolver.resolve(CTX);

      // -- ASSERT --
      assertThat(scope.unrestricted()).isTrue();
    }
  }

  @Nested
  @DisplayName("Grant")
  class GrantDimension {

    @Test
    @DisplayName("given no ACCESS_ASSESSMENT, should filter scenarios on the granted ids")
    void given_noAssessment_should_filterOnGrants() {
      // -- ARRANGE --
      grantCapabilities(ResourceType.ASSET, ResourceType.FINDING);
      RawGrant onScenario = grant("scenario-1", Grant.GRANT_RESOURCE_TYPE.SCENARIO);
      when(auth.getUser_grants()).thenReturn(Set.of(onScenario));

      // -- ACT --
      EngineReadScope scope = resolver.resolve(CTX);

      // -- ASSERT --
      List<EngineReadScope.GrantKey> keys = rulesOf(scope).grantFilters().get("scenario");
      assertThat(keys)
          .containsExactly(new EngineReadScope.GrantKey("base_id", Set.of("scenario-1")));
      assertThat(rulesOf(scope).grantFilters().get("finding"))
          .contains(new EngineReadScope.GrantKey("base_scenario_side", Set.of("scenario-1")));
    }

    @Test
    @DisplayName("given ACCESS_ASSESSMENT, should not filter on grants")
    void given_assessment_should_notFilterOnGrants() {
      // -- ARRANGE --
      grantCapabilities(
          ResourceType.SIMULATION, ResourceType.SCENARIO, ResourceType.ASSET, ResourceType.FINDING);

      // -- ACT --
      EngineReadScope scope = resolver.resolve(CTX);

      // -- ASSERT --
      assertThat(rulesOf(scope).grantFilters())
          .doesNotContainKeys("scenario", "simulation", "finding");
    }

    @Test
    @DisplayName("given no grant-managed type, should not filter assets on grants")
    void given_assets_should_notBeGrantFiltered() {
      // -- ARRANGE --
      grantCapabilities(ResourceType.ASSET);

      // -- ACT --
      EngineReadScope scope = resolver.resolve(CTX);

      // -- ASSERT --
      assertThat(rulesOf(scope).readableEntities()).contains("asset");
      assertThat(rulesOf(scope).grantFilters()).doesNotContainKey("asset");
    }
  }

  @Nested
  @DisplayName("Several tenants")
  class SeveralTenants {

    @Test
    @DisplayName("given different capabilities in two tenants, should resolve each tenant's rules")
    void given_differentCapabilitiesPerTenant_should_resolveRulesPerTenant() {
      // -- ARRANGE --
      // ACCESS_ASSETS and ACCESS_ASSESSMENT in tenant A only; nothing but open types in tenant B.
      Set<ResourceType> heldInA =
          Set.of(ResourceType.ASSET, ResourceType.SIMULATION, ResourceType.SCENARIO);
      when(permissionService.hasCapabilityPermissionInTenant(eq(user), any(), any(), any()))
          .thenAnswer(
              invocation ->
                  TENANT_A.equals(invocation.getArgument(1))
                      && heldInA.contains(invocation.<ResourceType>getArgument(2)));
      RawGrant onScenario = grant("scenario-b", Grant.GRANT_RESOURCE_TYPE.SCENARIO);
      when(auth.getUser_grants()).thenReturn(Set.of(onScenario));

      // -- ACT --
      EngineReadScope scope = resolver.resolve(TxCtx.forTenants(List.of(TENANT_A, TENANT_B)));

      // -- ASSERT --
      EngineReadScope.TenantRules inA = scope.tenantRules().get(TENANT_A);
      EngineReadScope.TenantRules inB = scope.tenantRules().get(TENANT_B);
      assertThat(inA.readableEntities()).contains("asset", "scenario");
      assertThat(inA.grantFilters()).doesNotContainKeys("scenario", "simulation");
      assertThat(inB.readableEntities()).doesNotContain("asset");
      assertThat(inB.grantFilters().get("scenario"))
          .containsExactly(new EngineReadScope.GrantKey("base_id", Set.of("scenario-b")));
    }

    @Test
    @DisplayName("given a scope not restricted to tenants, should read nothing")
    void given_unrestrictedTxCtx_should_failClosed() {
      // -- ACT --
      EngineReadScope scope = resolver.resolve(TxCtx.allTenants());

      // -- ASSERT --
      assertThat(scope.unrestricted()).isFalse();
      assertThat(scope.tenantRules()).isEmpty();
    }
  }

  @Test
  @DisplayName("given markings not active on assets, should hide no asset")
  void given_markingsInactive_should_hideNoAsset() {
    // -- ARRANGE --
    grantCapabilities(ResourceType.ASSET);

    // -- ACT --
    EngineReadScope scope = resolver.resolve(CTX);

    // -- ASSERT --
    assertThat(scope.hiddenAssetIds()).isEmpty();
  }

  @Test
  @DisplayName(
      "given an unrestricted reader without ACCESS_ASSESSMENT, should keep the legacy user")
  void given_noAssessment_should_keepLegacyGrantUser() {
    // -- ARRANGE --
    grantCapabilities(ResourceType.ASSET); // no SIMULATION READ, i.e. no ACCESS_ASSESSMENT

    // -- ACT --
    EngineReadScope scope = resolver.resolve(CTX);

    // -- ASSERT --
    assertThat(scope.legacyGrantUser()).isSameAs(auth);
  }
}
