package io.openaev.rest.finding;

import static io.openaev.database.audit.ModelBaseListener.DATA_PERSIST;
import static io.openaev.database.audit.ModelBaseListener.DATA_UPDATE;
import static io.openaev.utils.SensitiveValueMaskingUtils.MASK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.IntegrationTest;
import io.openaev.context.TxCtx;
import io.openaev.database.audit.BaseEvent;
import io.openaev.database.model.ContractOutputType;
import io.openaev.database.model.Finding;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/**
 * {@code FindingWriter.saveCompleteFinding} opens its own {@code REQUIRES_NEW} transaction, so the
 * caller's scope does not travel with it. Its two refusals are the difference between a legible
 * failure and a silent one, and neither was covered.
 *
 * <p>The upsert's conflict branch is rewritten to {@code DO UPDATE ... WHERE
 * can_access_tenant(findings.tenant_id) RETURNING finding_id}. Conflicting with a row owned by
 * another tenant updates nothing, {@code RETURNING} yields nothing, and the id comes back null. The
 * old behaviour inserted that null into {@code findings_assets} and surfaced as a NOT NULL
 * violation on a different table.
 *
 * <p>Not {@code @Transactional}: the writer's own {@code REQUIRES_NEW} boundary is the subject, and
 * the seed has to be committed for the conflict to happen at all.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=findings,assets")
@WithMockUser(isAdmin = true)
@RecordApplicationEvents
@DisplayName("the finding writer refuses a write it cannot attribute or cannot see")
class FindingWriterTest extends IntegrationTest {

  @Autowired private FindingWriter findingWriter;
  @Autowired private DataSource dataSource;
  @Autowired private ApplicationEvents applicationEvents;

  private JdbcTemplate jdbc;
  private String ownerTenant;
  private String otherTenant;
  private String injectId;
  private String assetId;
  private String secondAssetId;
  private final String field = "hostname";
  // A real enum name: the published event loads the finding back as an entity.
  private final String type = ContractOutputType.Text.name();
  private String value;

  @BeforeEach
  void seedAFindingOwnedByOneTenant() {
    jdbc = new JdbcTemplate(dataSource);
    ownerTenant = seedTenant("writer-owner-");
    otherTenant = seedTenant("writer-other-");
    injectId = UUID.randomUUID().toString();
    assetId = UUID.randomUUID().toString();
    secondAssetId = UUID.randomUUID().toString();
    value = "writer-" + UUID.randomUUID();

    jdbc.update(
        "INSERT INTO injects (inject_id, inject_title, inject_created_at, inject_updated_at,"
            + " inject_depends_duration, inject_all_teams, inject_enabled, tenant_id)"
            + " VALUES (?, 'writer inject', now(), now(), 0, false, true, ?)",
        injectId,
        ownerTenant);
    jdbc.update(
        "INSERT INTO assets (asset_id, asset_name, asset_type, asset_created_at, asset_updated_at,"
            + " tenant_id, asset_hostname, endpoint_platform, endpoint_arch)"
            + " VALUES (?, 'writer-asset', 'Endpoint', now(), now(), ?, 'writer-asset', 'Linux',"
            + " 'x86_64')",
        assetId,
        ownerTenant);
    // The asset of a second agent reporting the same finding.
    jdbc.update(
        "INSERT INTO assets (asset_id, asset_name, asset_type, asset_created_at, asset_updated_at,"
            + " tenant_id, asset_hostname, endpoint_platform, endpoint_arch)"
            + " VALUES (?, 'writer-asset-2', 'Endpoint', now(), now(), ?, 'writer-asset-2',"
            + " 'Linux', 'x86_64')",
        secondAssetId,
        ownerTenant);
    // The row the second write will conflict with, owned by ownerTenant.
    jdbc.update(
        "INSERT INTO findings (finding_id, finding_field, finding_type, finding_value,"
            + " finding_inject_id, finding_created_at, finding_updated_at, tenant_id)"
            + " VALUES (?, ?, ?, ?, ?, now(), now(), ?)",
        UUID.randomUUID().toString(),
        field,
        type,
        value,
        injectId,
        ownerTenant);
  }

  @AfterEach
  void sweep() {
    jdbc.update("DELETE FROM findings_assets WHERE asset_id IN (?, ?)", assetId, secondAssetId);
    for (String tenantId : new String[] {ownerTenant, otherTenant}) {
      jdbc.update("DELETE FROM findings WHERE tenant_id = ?", tenantId);
      jdbc.update("DELETE FROM assets WHERE tenant_id = ?", tenantId);
      jdbc.update("DELETE FROM injects WHERE tenant_id = ?", tenantId);
      jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", tenantId);
    }
  }

  @Test
  @DisplayName("a conflict with another tenant's finding is refused, naming the scope as the cause")
  void conflictOutsideTheScopeIsRefusedLoudly() {
    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class,
            () ->
                findingWriter.saveCompleteFinding(
                    TxCtx.forTenant(otherTenant),
                    field,
                    type,
                    value,
                    new String[0],
                    injectId,
                    "writer probe",
                    assetId,
                    new String[0]));

    assertTrue(
        thrown.getMessage().contains("outside the current tenant scope"),
        "the refusal must name the scope, not surface later as a NOT NULL violation on"
            + " findings_assets: "
            + thrown.getMessage());

    // And nothing was written on the way to the refusal.
    assertEquals(
        0L,
        jdbc.queryForObject(
            "SELECT count(*) FROM findings WHERE tenant_id = ?", Long.class, otherTenant),
        "the refused write must leave no row behind");
  }

  @Test
  @DisplayName("the same write inside the owning tenant's scope succeeds")
  void conflictInsideTheScopeUpdatesTheRow() {
    // The positive half. Without it the refusal above would also hold if the writer refused every
    // conflict, and this suite would say nothing about the scope actually being the discriminator.
    findingWriter.saveCompleteFinding(
        TxCtx.forTenant(ownerTenant),
        field,
        type,
        value,
        new String[0],
        injectId,
        "writer probe",
        assetId,
        new String[0]);

    assertEquals(
        1L,
        jdbc.queryForObject(
            "SELECT count(*) FROM findings WHERE tenant_id = ? AND finding_value = ?",
            Long.class,
            ownerTenant,
            value),
        "the conflict must resolve to an update of the existing row, not a second one");
    assertEquals(
        1L,
        jdbc.queryForObject(
            "SELECT count(*) FROM findings_assets WHERE asset_id = ?", Long.class, assetId),
        "the asset link must be written with the resolved finding id");
  }

  @Test
  @DisplayName("an agent finding is stored with the value the platform displays (#7893)")
  void given_aSensitiveAgentFinding_should_storeItsDisplayedValue() {
    // The native upsert skips the JPA callbacks, so the writer must pass the displayed value
    // itself: search, filters and sorts read it, never the cleartext.
    findingWriter.saveCompleteFinding(
        TxCtx.forTenant(ownerTenant),
        "credentials",
        "Credentials",
        "admin:motdepasse",
        new String[0],
        injectId,
        "writer probe",
        assetId,
        new String[0]);

    assertEquals(
        "admin:mo" + MASK,
        jdbc.queryForObject(
            "SELECT finding_value_display FROM findings WHERE tenant_id = ? AND finding_value = ?",
            String.class,
            ownerTenant,
            "admin:motdepasse"));
  }

  @Test
  @DisplayName("a row written before the displayed value existed gets it on its next detection")
  void given_aRowWithoutDisplayedValue_should_getItWhenDetectedAgain() {
    // The seeded row was inserted without finding_value_display, like a row written by a pod of
    // the previous version during a rolling deploy. The conflict branch must fill it.
    findingWriter.saveCompleteFinding(
        TxCtx.forTenant(ownerTenant),
        field,
        type,
        value,
        new String[0],
        injectId,
        "writer probe",
        assetId,
        new String[0]);

    assertEquals(
        value,
        jdbc.queryForObject(
            "SELECT finding_value_display FROM findings WHERE tenant_id = ? AND finding_value = ?",
            String.class,
            ownerTenant,
            value));
  }

  @Test
  @DisplayName("a scope that names no single tenant is refused, naming the inject")
  void scopeWithoutASingleTenantIsRefused() {
    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class,
            () ->
                findingWriter.saveCompleteFinding(
                    TxCtx.missing(),
                    field,
                    type,
                    "unattributed-" + UUID.randomUUID(),
                    new String[0],
                    injectId,
                    "writer probe",
                    assetId,
                    new String[0]));

    assertTrue(
        thrown.getMessage().contains(injectId),
        "the refusal must name the inject so the caller can find what it failed to resolve: "
            + thrown.getMessage());
  }

  /**
   * The writes are native, so no JPA listener publishes their lifecycle event: without the writer's
   * own, notification triggers on findings never fire for agent executions (#8649). The events
   * mirror a JPA save: each agent reports its own copy of a finding, and only a new row or a new
   * asset link changes the finding.
   */
  @Nested
  @DisplayName("lifecycle events")
  class LifecycleEvents {

    @Test
    @DisplayName("a new finding publishes a create, and the same agent reporting it again nothing")
    void given_newFindingReportedTwiceOnSameAsset_should_publishSingleCreate() {
      // Arrange
      String newValue = "writer-new-" + UUID.randomUUID();

      // Act
      writeInOwnerScope(newValue, assetId);
      writeInOwnerScope(newValue, assetId);

      // Assert
      List<BaseEvent> events = findingEvents();
      assertEquals(1, events.size(), "a repeated report changes nothing on the finding");
      assertEquals(DATA_PERSIST, events.get(0).getType());
      assertEquals(findingIdOf(newValue), events.get(0).getInstance().getId());
    }

    @Test
    @DisplayName("a second agent reporting the finding on another asset publishes an update")
    void given_newFindingReportedOnSecondAsset_should_publishCreateThenUpdate() {
      // Arrange
      String newValue = "writer-new-" + UUID.randomUUID();

      // Act
      writeInOwnerScope(newValue, assetId);
      writeInOwnerScope(newValue, secondAssetId);

      // Assert: the update is what lets a trigger filtered on the second asset match
      List<BaseEvent> events = findingEvents();
      assertEquals(
          List.of(DATA_PERSIST, DATA_UPDATE), events.stream().map(BaseEvent::getType).toList());
      String findingId = findingIdOf(newValue);
      assertTrue(events.stream().allMatch(event -> findingId.equals(event.getInstance().getId())));
      assertEquals(
          2,
          ((Finding) events.get(1).getInstance()).getAssets().size(),
          "the update must carry the finding with both assets");
    }

    @Test
    @DisplayName("an existing finding already linked to the asset publishes nothing")
    void given_existingFindingAlreadyLinkedToAsset_should_publishNothing() {
      // Arrange: the first write links the seeded finding to the asset
      writeInOwnerScope(value, assetId);
      applicationEvents.clear();

      // Act
      writeInOwnerScope(value, assetId);

      // Assert
      assertTrue(findingEvents().isEmpty());
    }

    private void writeInOwnerScope(String findingValue, String reportingAssetId) {
      findingWriter.saveCompleteFinding(
          TxCtx.forTenant(ownerTenant),
          field,
          type,
          findingValue,
          new String[0],
          injectId,
          "writer probe",
          reportingAssetId,
          new String[0]);
    }

    private List<BaseEvent> findingEvents() {
      return applicationEvents.stream(BaseEvent.class)
          .filter(event -> event.getInstance() instanceof Finding)
          .toList();
    }

    private String findingIdOf(String findingValue) {
      return jdbc.queryForObject(
          "SELECT finding_id FROM findings WHERE finding_inject_id = ? AND finding_value = ?",
          String.class,
          injectId,
          findingValue);
    }
  }

  private String seedTenant(String prefix) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        id,
        prefix + id);
    return id;
  }
}
