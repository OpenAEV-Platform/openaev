package io.openaev.service.chaining;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.database.model.*;
import io.openaev.database.repository.CollectorRepository;
import io.openaev.database.repository.TeamRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.service.AssetGroupService;
import io.openaev.service.AssetService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the ADR-006 snapshot service: read-time status derivation (the three reference
 * points and their precedence), the security-platform reinstall / reconfiguration signals, and the
 * launch freeze (RUN-only security platform rows, isolation from allow / deny targeting).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ScopeSnapshotService")
class ScopeSnapshotServiceTest {

  private static final Instant LAUNCH_TIME = Instant.parse("2026-08-01T10:00:00Z");
  private static final Instant LATER_TIME = Instant.parse("2026-08-02T10:00:00Z");

  @Mock private AssetService assetService;
  @Mock private AssetGroupService assetGroupService;
  @Mock private CollectorRepository collectorRepository;
  @Mock private TeamRepository teamRepository;
  @Mock private UserRepository userRepository;

  @Mock private TenantScopedTransaction tenantTx;
  @InjectMocks private ScopeSnapshotService scopeSnapshotService;

  @BeforeEach
  void setUp() {
    lenient()
        .when(tenantTx.executeNew(any(), any(Supplier.class)))
        .thenAnswer(
            invocation -> {
              Supplier<?> supplier = invocation.getArgument(1);
              return supplier.get();
            });
  }

  // -- helpers --

  private static Asset asset(String id, String name) {
    Asset asset = new Asset();
    asset.setId(id);
    asset.setName(name);
    return asset;
  }

  private static ScopeRuleSnapshot assetPhoto(String id, String name) {
    return ScopeRuleSnapshot.builder()
        .label(name)
        .assets(
            List.of(
                ScopeRuleSnapshot.AssetSnapshot.builder()
                    .id(id)
                    .name(name)
                    .agentsCount(0)
                    .executors(List.of())
                    .build()))
        .build();
  }

  private static WorkflowScopeRule assetRule(
      String assetId, ScopeRuleSnapshot launch, ScopeRuleSnapshot end) {
    return WorkflowScopeRule.builder()
        .selectedMode(ScopeRuleSelectedMode.ALLOWLIST)
        .ruleSource(ScopeRuleSource.ASSET)
        .valueType(ScopeRuleValueType.ASSET_ID)
        .ruleValue(assetId)
        .snapshotStart(launch)
        .snapshotEnd(end)
        .build();
  }

  private static Team team(String id, String name) {
    Team team = new Team();
    team.setId(id);
    team.setName(name);
    return team;
  }

  private static User player(String id, String firstname, String lastname, String email) {
    User user = new User();
    user.setId(id);
    user.setFirstname(firstname);
    user.setLastname(lastname);
    user.setEmail(email);
    return user;
  }

  private static ScopeRuleSnapshot labelPhoto(String label) {
    return ScopeRuleSnapshot.builder().label(label).build();
  }

  private static WorkflowScopeRule teamRule(
      String teamId, ScopeRuleSnapshot launch, ScopeRuleSnapshot end) {
    return WorkflowScopeRule.builder()
        .selectedMode(ScopeRuleSelectedMode.ALLOWLIST)
        .ruleSource(ScopeRuleSource.TEAM)
        .valueType(ScopeRuleValueType.TEAM_ID)
        .ruleValue(teamId)
        .snapshotStart(launch)
        .snapshotEnd(end)
        .build();
  }

  private static WorkflowScopeRule playerRule(
      String userId, ScopeRuleSnapshot launch, ScopeRuleSnapshot end) {
    return WorkflowScopeRule.builder()
        .selectedMode(ScopeRuleSelectedMode.ALLOWLIST)
        .ruleSource(ScopeRuleSource.PLAYER)
        .valueType(ScopeRuleValueType.PLAYER_ID)
        .ruleValue(userId)
        .snapshotStart(launch)
        .snapshotEnd(end)
        .build();
  }

  private static ScopeRuleSnapshot platformPhoto(String id, String name, Instant updatedAt) {
    return ScopeRuleSnapshot.builder()
        .label(name)
        .securityPlatform(
            ScopeRuleSnapshot.SecurityPlatformSnapshot.builder()
                .id(id)
                .type("EDR")
                .updatedAt(updatedAt)
                .build())
        .build();
  }

  private static WorkflowScopeRule platformRule(
      String platformId, ScopeRuleSnapshot launch, ScopeRuleSnapshot end) {
    return WorkflowScopeRule.builder()
        .ruleSource(ScopeRuleSource.SECURITY_PLATFORM)
        .valueType(ScopeRuleValueType.SECURITY_PLATFORM_ID)
        .ruleValue(platformId)
        .snapshotStart(launch)
        .snapshotEnd(end)
        .build();
  }

  private static SecurityPlatform securityPlatform(String id, String name, Instant updatedAt) {
    SecurityPlatform platform =
        new SecurityPlatform(
            id, "SecurityPlatform", name, SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR);
    platform.setUpdatedAt(updatedAt);
    return platform;
  }

  // -- STATUS DERIVATION --

  @Nested
  @DisplayName("computeStatus - asset rules")
  class AssetStatusTests {

    @Test
    @DisplayName("should return null without a launch snapshot (draft / scenario / pre-ADR-006)")
    void shouldReturnNullWithoutLaunchSnapshot() {
      WorkflowScopeRule rule = assetRule("a1", null, null);

      assertNull(scopeSnapshotService.computeStatus(rule));
    }

    @Test
    @DisplayName("should resolve RESOLVED when nothing changed across launch, end and current")
    void shouldResolveWhenUnchanged() {
      when(assetService.asset("a1")).thenReturn(asset("a1", "Prod server"));
      WorkflowScopeRule rule =
          assetRule("a1", assetPhoto("a1", "Prod server"), assetPhoto("a1", "Prod server"));

      assertEquals(ScopeRuleSnapshotStatus.RESOLVED, scopeSnapshotService.computeStatus(rule));
    }

    @Test
    @DisplayName("should flag MODIFIED_DURING_EXECUTION while running when the live state differs")
    void shouldFlagModifiedDuringExecutionWhileRunning() {
      when(assetService.asset("a1")).thenReturn(asset("a1", "Renamed server"));
      // No end snapshot: the run is still RUNNING, the live state is the moving end reference.
      WorkflowScopeRule rule = assetRule("a1", assetPhoto("a1", "Prod server"), null);

      assertEquals(
          ScopeRuleSnapshotStatus.MODIFIED_DURING_EXECUTION,
          scopeSnapshotService.computeStatus(rule));
    }

    @Test
    @DisplayName("should flag DELETED_DURING_EXECUTION while running when the target is gone")
    void shouldFlagDeletedDuringExecutionWhileRunning() {
      when(assetService.asset("a1")).thenThrow(new ElementNotFoundException("gone"));
      WorkflowScopeRule rule = assetRule("a1", assetPhoto("a1", "Prod server"), null);

      assertEquals(
          ScopeRuleSnapshotStatus.DELETED_DURING_EXECUTION,
          scopeSnapshotService.computeStatus(rule));
    }

    @Test
    @DisplayName("should flag MODIFIED_AFTER_EXECUTION when the change happened after the run end")
    void shouldFlagModifiedAfterExecution() {
      when(assetService.asset("a1")).thenReturn(asset("a1", "Renamed server"));
      WorkflowScopeRule rule =
          assetRule("a1", assetPhoto("a1", "Prod server"), assetPhoto("a1", "Prod server"));

      assertEquals(
          ScopeRuleSnapshotStatus.MODIFIED_AFTER_EXECUTION,
          scopeSnapshotService.computeStatus(rule));
    }

    @Test
    @DisplayName("should flag DELETED_AFTER_EXECUTION when the target disappeared after the run")
    void shouldFlagDeletedAfterExecution() {
      when(assetService.asset("a1")).thenThrow(new ElementNotFoundException("gone"));
      WorkflowScopeRule rule =
          assetRule("a1", assetPhoto("a1", "Prod server"), assetPhoto("a1", "Prod server"));

      assertEquals(
          ScopeRuleSnapshotStatus.DELETED_AFTER_EXECUTION,
          scopeSnapshotService.computeStatus(rule));
    }

    @Test
    @DisplayName("should let a during-execution change dominate a later after-execution change")
    void shouldPreferDuringExecutionOverAfterExecution() {
      when(assetService.asset("a1")).thenReturn(asset("a1", "Renamed twice"));
      // launch != end (changed during the run) AND end != current (changed again after).
      WorkflowScopeRule rule =
          assetRule("a1", assetPhoto("a1", "Prod server"), assetPhoto("a1", "Renamed once"));

      assertEquals(
          ScopeRuleSnapshotStatus.MODIFIED_DURING_EXECUTION,
          scopeSnapshotService.computeStatus(rule));
    }

    @Test
    @DisplayName("should always resolve RESOLVED for a MANUAL rule (the value is the label)")
    void shouldAlwaysResolveManualRules() {
      ScopeRuleSnapshot photo = ScopeRuleSnapshot.builder().label("10.0.0.1").build();
      WorkflowScopeRule rule =
          WorkflowScopeRule.builder()
              .selectedMode(ScopeRuleSelectedMode.ALLOWLIST)
              .ruleSource(ScopeRuleSource.MANUAL)
              .valueType(ScopeRuleValueType.IP)
              .ruleValue("10.0.0.1")
              .snapshotStart(photo)
              .snapshotEnd(photo)
              .build();

      assertEquals(ScopeRuleSnapshotStatus.RESOLVED, scopeSnapshotService.computeStatus(rule));
    }
  }

  @Nested
  @DisplayName("computeStatus - audience rules (team / player)")
  class AudienceStatusTests {

    @Test
    @DisplayName("should resolve RESOLVED when the frozen team name still matches the live name")
    void shouldResolveWhenTeamUnchanged() {
      TenantContext.setCurrentTenant("tenant-1");
      try {
        when(teamRepository.findByIdAndTenantId("t1", "tenant-1"))
            .thenReturn(Optional.of(team("t1", "It team")));
        WorkflowScopeRule rule = teamRule("t1", labelPhoto("It team"), null);

        assertEquals(ScopeRuleSnapshotStatus.RESOLVED, scopeSnapshotService.computeStatus(rule));
      } finally {
        TenantContext.clearCurrentTenant();
      }
    }

    @Test
    @DisplayName("should flag MODIFIED_DURING_EXECUTION when the team was renamed while running")
    void shouldFlagRenamedTeamWhileRunning() {
      TenantContext.setCurrentTenant("tenant-1");
      try {
        when(teamRepository.findByIdAndTenantId("t1", "tenant-1"))
            .thenReturn(Optional.of(team("t1", "Blue team")));
        WorkflowScopeRule rule = teamRule("t1", labelPhoto("It team"), null);

        assertEquals(
            ScopeRuleSnapshotStatus.MODIFIED_DURING_EXECUTION,
            scopeSnapshotService.computeStatus(rule));
      } finally {
        TenantContext.clearCurrentTenant();
      }
    }

    @Test
    @DisplayName("should flag DELETED_DURING_EXECUTION when the team is gone while running")
    void shouldFlagDeletedTeamWhileRunning() {
      TenantContext.setCurrentTenant("tenant-1");
      try {
        when(teamRepository.findByIdAndTenantId("t1", "tenant-1")).thenReturn(Optional.empty());
        WorkflowScopeRule rule = teamRule("t1", labelPhoto("It team"), null);

        assertEquals(
            ScopeRuleSnapshotStatus.DELETED_DURING_EXECUTION,
            scopeSnapshotService.computeStatus(rule));
      } finally {
        TenantContext.clearCurrentTenant();
      }
    }

    @Test
    @DisplayName("should resolve RESOLVED when the frozen player name still matches name-or-email")
    void shouldResolveWhenPlayerUnchanged() {
      TenantContext.setCurrentTenant("tenant-1");
      try {
        when(userRepository.findAllByIdInAndTenantId(List.of("p1"), "tenant-1"))
            .thenReturn(List.of(player("p1", "John", "Doe", "john.doe@filigran.io")));
        WorkflowScopeRule rule = playerRule("p1", labelPhoto("John Doe"), null);

        assertEquals(ScopeRuleSnapshotStatus.RESOLVED, scopeSnapshotService.computeStatus(rule));
      } finally {
        TenantContext.clearCurrentTenant();
      }
    }

    @Test
    @DisplayName(
        "should return null for a degraded pre-resolution photo (raw-id label) instead of deriving a false rename")
    void shouldIgnoreDegradedAudiencePhoto() {
      // Photo frozen before TEAM / PLAYER resolution existed: label == raw id. Deriving a status
      // would compare the id against the now-resolvable name and misread it as a mid-run rename.
      WorkflowScopeRule rule = teamRule("t1", labelPhoto("t1"), null);

      assertNull(scopeSnapshotService.computeStatus(rule));
      verifyNoInteractions(teamRepository);
    }
  }

  @Nested
  @DisplayName("computeStatus - security platform rules")
  class SecurityPlatformStatusTests {

    @Test
    @DisplayName("should resolve RESOLVED when the platform is untouched")
    void shouldResolveWhenPlatformUntouched() {
      when(assetService.asset("sp1"))
          .thenReturn(securityPlatform("sp1", "CrowdStrike", LAUNCH_TIME));
      ScopeRuleSnapshot photo = platformPhoto("sp1", "CrowdStrike", LAUNCH_TIME);
      WorkflowScopeRule rule = platformRule("sp1", photo, photo);

      assertEquals(ScopeRuleSnapshotStatus.RESOLVED, scopeSnapshotService.computeStatus(rule));
    }

    @Test
    @DisplayName(
        "should flag a reconfiguration (same id, later updatedAt) as MODIFIED_AFTER_EXECUTION")
    void shouldFlagReconfigurationAsModified() {
      when(assetService.asset("sp1"))
          .thenReturn(securityPlatform("sp1", "CrowdStrike", LATER_TIME));
      ScopeRuleSnapshot photo = platformPhoto("sp1", "CrowdStrike", LAUNCH_TIME);
      WorkflowScopeRule rule = platformRule("sp1", photo, photo);

      assertEquals(
          ScopeRuleSnapshotStatus.MODIFIED_AFTER_EXECUTION,
          scopeSnapshotService.computeStatus(rule));
    }

    @Test
    @DisplayName(
        "should flag an uninstall / reinstall (frozen id gone) as DELETED_DURING_EXECUTION while running")
    void shouldFlagReinstallAsDeleted() {
      when(assetService.asset("sp1")).thenThrow(new ElementNotFoundException("reinstalled"));
      WorkflowScopeRule rule =
          platformRule("sp1", platformPhoto("sp1", "CrowdStrike", LAUNCH_TIME), null);

      assertEquals(
          ScopeRuleSnapshotStatus.DELETED_DURING_EXECUTION,
          scopeSnapshotService.computeStatus(rule));
    }
  }

  // -- FREEZE --

  @Nested
  @DisplayName("freezeLaunch / freezeEnd")
  class FreezeTests {

    @Test
    @DisplayName(
        "should append one isolated SECURITY_PLATFORM row per connected platform at launch")
    void shouldAppendIsolatedSecurityPlatformRowsAtLaunch() {
      SecurityPlatform platform = securityPlatform("sp1", "CrowdStrike", LAUNCH_TIME);
      Collector collector = new Collector();
      collector.setSecurityPlatform(platform);
      when(collectorRepository.findAllByTenantIdAndSecurityPlatformIsNotNull("tenant-1"))
          .thenReturn(List.of(collector));
      when(assetService.asset("sp1")).thenReturn(platform);

      Workflow run = Workflow.builder().status(WorkflowStatus.RUN).build();
      scopeSnapshotService.freezeLaunch(run, "tenant-1");

      assertEquals(1, run.getWorkflowScopeRules().size());
      WorkflowScopeRule platformRow = run.getWorkflowScopeRules().getFirst();
      assertEquals(ScopeRuleSource.SECURITY_PLATFORM, platformRow.getRuleSource());
      assertEquals(ScopeRuleValueType.SECURITY_PLATFORM_ID, platformRow.getValueType());
      assertEquals("sp1", platformRow.getRuleValue());
      // Isolation from execution targeting: no selectedMode, so the allow / deny resolution
      // (ScopeService / getValidAssets / getAllowlist) never picks the row up. See ADR-006.
      assertNull(platformRow.getSelectedMode());
      assertTrue(run.getAllowlist().isEmpty());
      assertTrue(run.getDenylist().isEmpty());
      // The launch photo is frozen on the appended row.
      assertNotNull(platformRow.getSnapshotStart());
      assertEquals("CrowdStrike", platformRow.getSnapshotStart().getLabel());
      assertEquals("sp1", platformRow.getSnapshotStart().getSecurityPlatform().getId());
      assertEquals(
          LAUNCH_TIME, platformRow.getSnapshotStart().getSecurityPlatform().getUpdatedAt());
      assertNull(platformRow.getSnapshotEnd());
    }

    @Test
    @DisplayName(
        "should freeze the launch photo on copied rules and degrade a missing target to its raw id")
    void shouldFreezeLaunchPhotoAndDegradeMissingTarget() {
      when(assetService.asset("a1")).thenReturn(asset("a1", "Prod server"));
      when(assetService.asset("gone")).thenThrow(new ElementNotFoundException("gone"));
      when(collectorRepository.findAllByTenantIdAndSecurityPlatformIsNotNull(anyString()))
          .thenReturn(List.of());

      WorkflowScopeRule resolvable = assetRule("a1", null, null);
      WorkflowScopeRule unresolvable = assetRule("gone", null, null);
      Workflow run = Workflow.builder().status(WorkflowStatus.RUN).build();
      run.getWorkflowScopeRules().add(resolvable);
      run.getWorkflowScopeRules().add(unresolvable);

      scopeSnapshotService.freezeLaunch(run, "tenant-1");

      assertEquals("Prod server", resolvable.getSnapshotStart().getLabel());
      assertEquals(1, resolvable.getSnapshotStart().getAssets().size());
      // Missing target: the photo degrades to the raw id label instead of staying null.
      assertEquals("gone", unresolvable.getSnapshotStart().getLabel());
      assertNull(unresolvable.getSnapshotStart().getAssets());
    }

    @Test
    @DisplayName(
        "should mark a missing target as an explicit deleted end photo, derived as DELETED_DURING_EXECUTION")
    void shouldMarkMissingTargetAsDeletedEndPhoto() {
      when(assetService.asset("a1")).thenThrow(new ElementNotFoundException("gone"));

      WorkflowScopeRule rule = assetRule("a1", assetPhoto("a1", "Prod server"), null);
      Workflow run = Workflow.builder().status(WorkflowStatus.RUN).build();
      run.getWorkflowScopeRules().add(rule);

      scopeSnapshotService.freezeEnd(run);

      // The end photo exists (the run HAS ended) and records the deletion explicitly, keeping the
      // last known launch label - never a raw-id label the diff would misread as a rename.
      assertNotNull(rule.getSnapshotEnd());
      assertEquals(Boolean.TRUE, rule.getSnapshotEnd().getDeleted());
      assertEquals("Prod server", rule.getSnapshotEnd().getLabel());
      assertEquals(
          ScopeRuleSnapshotStatus.DELETED_DURING_EXECUTION,
          scopeSnapshotService.computeStatus(rule));
    }

    @Test
    @DisplayName("should freeze team and player photos with resolved names, never raw ids")
    void shouldFreezeAudiencePhotosWithResolvedNames() {
      TenantContext.setCurrentTenant("tenant-1");
      try {
        when(collectorRepository.findAllByTenantIdAndSecurityPlatformIsNotNull(anyString()))
            .thenReturn(List.of());
        when(teamRepository.findByIdAndTenantId("t1", "tenant-1"))
            .thenReturn(Optional.of(team("t1", "It team")));
        when(userRepository.findAllByIdInAndTenantId(List.of("p1"), "tenant-1"))
            .thenReturn(List.of(player("p1", "John", "Doe", "john.doe@filigran.io")));
        when(userRepository.findAllByIdInAndTenantId(List.of("p2"), "tenant-1"))
            .thenReturn(List.of(player("p2", null, null, "jane@filigran.io")));

        WorkflowScopeRule teamRule = teamRule("t1", null, null);
        WorkflowScopeRule namedPlayer = playerRule("p1", null, null);
        WorkflowScopeRule emailOnlyPlayer = playerRule("p2", null, null);
        Workflow run = Workflow.builder().status(WorkflowStatus.RUN).build();
        run.getWorkflowScopeRules().add(teamRule);
        run.getWorkflowScopeRules().add(namedPlayer);
        run.getWorkflowScopeRules().add(emailOnlyPlayer);

        scopeSnapshotService.freezeLaunch(run, "tenant-1");

        assertEquals("It team", teamRule.getSnapshotStart().getLabel());
        assertEquals("John Doe", namedPlayer.getSnapshotStart().getLabel());
        // A player without first / last name freezes the email, mirroring the frontend display.
        assertEquals("jane@filigran.io", emailOnlyPlayer.getSnapshotStart().getLabel());
      } finally {
        TenantContext.clearCurrentTenant();
      }
    }

    @Test
    @DisplayName("should freeze the end photo on every rule when the run ends")
    void shouldFreezeEndPhotoOnEveryRule() {
      when(assetService.asset("a1")).thenReturn(asset("a1", "Renamed server"));

      WorkflowScopeRule rule = assetRule("a1", assetPhoto("a1", "Prod server"), null);
      Workflow run = Workflow.builder().status(WorkflowStatus.RUN).build();
      run.getWorkflowScopeRules().add(rule);

      scopeSnapshotService.freezeEnd(run);

      assertNotNull(rule.getSnapshotEnd());
      assertEquals("Renamed server", rule.getSnapshotEnd().getLabel());
      // The launch photo is untouched (structural immutability of the two columns).
      assertEquals("Prod server", rule.getSnapshotStart().getLabel());
    }
  }
}
