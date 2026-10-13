package io.openaev.service.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
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
import io.openaev.service.PermissionService;
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
 * How the reader scope is resolved from the source of truth. The marking lookup itself is covered
 * against real rows by the dashboard API tests; here markings are off ({@code assets} not active).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Engine read scope resolution")
class EngineReadScopeResolverTest {

  private static final TxCtx CTX = TxCtx.forTenant("tenant-a");

  @Mock private PermissionService permissionService;
  @Mock private UserService userService;
  @Mock private UserRepository userRepository;
  @Mock private RawUserAuthMapper rawUserAuthMapper;
  @Mock private MarkingScopeSupplier markingScopeSupplier;
  @Mock private JdbcTemplate jdbcTemplate;
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
            jdbcTemplate);
    when(userService.currentUser()).thenReturn(user);
    lenient().when(user.getId()).thenReturn("user-1");
    lenient().when(rawUserAuthMapper.toRawUserAuth(any())).thenReturn(auth);
    lenient().when(auth.getUser_grants()).thenReturn(Set.of());
  }

  private void grantCapabilities(ResourceType... granted) {
    Set<ResourceType> held = Set.of(granted);
    lenient()
        .when(permissionService.hasCapabilityPermission(eq(user), any(), any()))
        .thenAnswer(invocation -> held.contains(invocation.<ResourceType>getArgument(1)));
  }

  private static RawGrant grant(String resourceId, Grant.GRANT_RESOURCE_TYPE type) {
    RawGrant grant = mock(RawGrant.class);
    when(grant.getGrant_resource()).thenReturn(resourceId);
    when(grant.getGrant_resource_type()).thenReturn(type);
    return grant;
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
      assertThat(scope.readableEntities()).doesNotContain("asset", "vulnerable-endpoint");
      assertThat(scope.readableEntities()).contains("finding", "scenario", "tag");
    }

    @Test
    @DisplayName("given every capability and no marking, should be unrestricted")
    void given_everyCapability_should_beUnrestricted() {
      // -- ARRANGE --
      lenient()
          .when(permissionService.hasCapabilityPermission(eq(user), any(), any()))
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
      List<EngineReadScope.GrantKey> keys = scope.grantFilters().get("scenario");
      assertThat(keys)
          .containsExactly(new EngineReadScope.GrantKey("base_id", Set.of("scenario-1")));
      assertThat(scope.grantFilters().get("finding"))
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
      assertThat(scope.grantFilters()).doesNotContainKeys("scenario", "simulation", "finding");
    }

    @Test
    @DisplayName("given no grant-managed type, should not filter assets on grants")
    void given_assets_should_notBeGrantFiltered() {
      // -- ARRANGE --
      grantCapabilities(ResourceType.ASSET);

      // -- ACT --
      EngineReadScope scope = resolver.resolve(CTX);

      // -- ASSERT --
      assertThat(scope.readableEntities()).contains("asset");
      assertThat(scope.grantFilters()).doesNotContainKey("asset");
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
