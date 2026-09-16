package io.openaev.scheduler.jobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.service.AssetGroupService;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * {@code InjectsFinalizationJob.handlePendingInject} resolves the agents of every pending inject,
 * and for an inject targeting an asset group that goes through {@code
 * AssetGroupService.assetsFromAssetGroup(id)}, which loads the group by id.
 *
 * <p>Unlike the sibling {@code handleAutoClosingSimulations}, that handler opens no tenant scope.
 * It relies on {@code disableFilter("tenantFilter")}, the v1 cross-tenant idiom, which is inert
 * under v2: the statement inspector rewrites by SQL text and filters a {@code findById} just like
 * any other read. With no scope the lookup finds nothing and {@code assetGroup(id)} throws {@link
 * ElementNotFoundException}, which propagates out of the job.
 *
 * <p>This is not a silent degradation. The exception escapes to {@code execute()}, which rethrows
 * it as a {@code JobExecutionException}, so a single inject targeting an asset group stops the
 * whole finalization sweep for every tenant.
 *
 * <p>The class is not {@code @Transactional}: the scoped primitive refuses to open inside an active
 * transaction, so seeding goes through an auto-committing {@link JdbcTemplate} and is swept
 * explicitly.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=asset_groups")
@WithMockUser(isAdmin = true)
@DisplayName("resolving an inject's asset group needs a tenant scope, and the job has none")
class PendingInjectAssetGroupScopeTest extends IntegrationTest {

  @Autowired private AssetGroupService assetGroupService;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private String tenantId;
  private String assetGroupId;

  @BeforeEach
  void seedAnAssetGroupInItsOwnTenant() {
    jdbc = new JdbcTemplate(dataSource);
    tenantId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        tenantId,
        "pending-inject-scope-" + tenantId);
    assetGroupId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO asset_groups (asset_group_id, asset_group_name, asset_group_dynamic_filter,"
            + " asset_group_created_at, asset_group_updated_at, tenant_id)"
            + " VALUES (?, ?, CAST('{}' AS json), now(), now(), ?)",
        assetGroupId,
        "pending-inject-group",
        tenantId);
  }

  @AfterEach
  void sweep() {
    jdbc.update("DELETE FROM asset_groups WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", tenantId);
  }

  @Test
  @DisplayName("inside the owning tenant's scope the group resolves")
  void scopedLookupResolvesTheGroup() {
    List<?> assets =
        tenantTx.execute(
            TxCtx.forTenant(tenantId), () -> assetGroupService.assetsFromAssetGroup(assetGroupId));
    assertEquals(0, assets.size(), "the group has no member yet, but it must resolve");
  }

  @Test
  @DisplayName("with no scope the lookup throws, which is what stops the finalization job")
  void unscopedLookupThrows() {
    // Asserted, not described: if this ever stops throwing, either the scope handling changed or
    // the table left active-tables, and handlePendingInject's scoping must be revisited.
    assertThrows(
        ElementNotFoundException.class,
        () -> assetGroupService.assetsFromAssetGroup(assetGroupId),
        "an unscoped asset-group lookup is expected to fail closed and throw");
  }
}
