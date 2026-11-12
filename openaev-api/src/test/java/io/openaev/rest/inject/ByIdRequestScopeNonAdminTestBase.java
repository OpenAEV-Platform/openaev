package io.openaev.rest.inject;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Grant;
import io.openaev.database.model.User;
import io.openaev.rest.inject.form.InjectIndividualExportRequestInput;
import io.openaev.rest.inject.form.InjectInput;
import io.openaev.rest.inject.form.InjectReceptionInput;
import io.openaev.utils.fixtures.GrantFixture;
import io.openaev.utils.fixtures.TenantGroupFixture;
import io.openaev.utils.fixtures.TenantRoleFixture;
import io.openaev.utils.fixtures.composers.GrantComposer;
import io.openaev.utils.fixtures.composers.TenantGroupComposer;
import io.openaev.utils.fixtures.composers.TenantRoleComposer;
import io.openaev.utils.mockUser.TestUserHolder;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Adds a caller who holds real tenant-scoped standing (a capability or a resource grant) instead of
 * platform-admin bypass, on top of the atomic-testing and plain-inject by-id seeding and routes of
 * {@link ByIdRequestScopeTestBase}.
 */
abstract class ByIdRequestScopeNonAdminTestBase extends ByIdRequestScopeTestBase {

  @Autowired private TestUserHolder testUserHolder;
  @Autowired private TenantGroupComposer tenantGroupComposer;
  @Autowired private TenantRoleComposer tenantRoleComposer;
  @Autowired private GrantComposer grantComposer;
  @Autowired private EntityManager entityManager;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private TenantScopedTransaction tenantScopedTransaction;

  /**
   * Attaches the current caller to a new tenant with a role carrying every tenant-scoped capability
   * (the ceiling a tenant role can hold short of the platform BYPASS escape hatch): the standing a
   * tenant's top role grants on every assessment resource regardless of which one.
   */
  String memberWithEveryTenantCapability(String tenantName) throws Exception {
    return tenantHelper
        .createTenantWithCapabilities(tenantName, Capability.allTenantScoped())
        .getId();
  }

  /**
   * Attaches the current caller to {@code tenantId} with no capability at all, and a single grant
   * on {@code resourceId}: the standing of a plain member whose only access to anything comes from
   * being named on that one resource.
   */
  void memberWithGrantOnResourceOnly(
      String tenantId,
      String resourceId,
      Grant.GRANT_RESOURCE_TYPE resourceType,
      Grant.GRANT_TYPE grantType) {
    tenantHelper.attachCurrentUserToTenant(tenantId);
    String previousTenant =
        TenantContext.hasCurrentTenant() ? TenantContext.getCurrentTenant() : null;
    TenantContext.setCurrentTenant(tenantId);
    try {
      new TransactionTemplate(transactionManager)
          .executeWithoutResult(
              status -> {
                // The role of the tenant is a row of an active table: written in its scope only
                tenantScopedTransaction.setScopeOnCurrentTransaction(TxCtx.forTenant(tenantId));
                User user = testUserHolder.get();
                TenantRoleComposer.Composer roleComposer =
                    tenantRoleComposer.forRole(TenantRoleFixture.getRole(Set.of()));
                var group = TenantGroupFixture.getGroup();
                group.setUsers(List.of(user));
                GrantComposer.Composer grant =
                    grantComposer.forGrant(
                        GrantFixture.getGrant(resourceId, resourceType, grantType, null));
                tenantGroupComposer
                    .forGroup(group)
                    .withRole(roleComposer)
                    .withGrant(grant)
                    .persist();
                entityManager.flush();
                entityManager.clear();
              });
    } finally {
      if (previousTenant != null) {
        TenantContext.setCurrentTenant(previousTenant);
      } else {
        TenantContext.clearCurrentTenant();
      }
    }
  }

  // -- ADDITIONAL REQUESTS --

  int injectExport(Route route, String id) throws Exception {
    InjectIndividualExportRequestInput input = new InjectIndividualExportRequestInput();
    return performStatus(
        route.with(
            post(route.inject(id) + "/inject_export")
                .content(asJsonString(input))
                .contentType(MediaType.APPLICATION_JSON)));
  }

  int injectTargetSearch(Route route, String id, String targetType) throws Exception {
    return performStatus(
        route.with(
            post(route.inject(id) + "/targets/" + targetType + "/search")
                .content(asJsonString(new SearchPaginationInput()))
                .contentType(MediaType.APPLICATION_JSON)));
  }

  int injectTargetOptions(Route route, String id, String targetType) throws Exception {
    return performStatus(route.with(get(route.inject(id) + "/targets/" + targetType + "/options")));
  }

  int injectExecutionReception(Route route, String id) throws Exception {
    InjectReceptionInput input = new InjectReceptionInput();
    String uri = injectsBaseUri(route) + "/execution/reception/" + id;
    return performStatus(
        route.with(post(uri).content(asJsonString(input)).contentType(MediaType.APPLICATION_JSON)));
  }

  /**
   * {@code PUT /injects/{exerciseId}/{injectId}}: updates {@code injectId} under {@code
   * exerciseId}.
   */
  int updateInject(Route route, String exerciseId, String injectId, String title) throws Exception {
    InjectInput input = new InjectInput();
    input.setTitle(title);
    String uri = injectsBaseUri(route) + "/" + exerciseId + "/" + injectId;
    return performStatus(
        route.with(put(uri).content(asJsonString(input)).contentType(MediaType.APPLICATION_JSON)));
  }

  private int performStatus(MockHttpServletRequestBuilder request) throws Exception {
    TenantContext.clearCurrentTenant();
    return mvc.perform(request).andReturn().getResponse().getStatus();
  }
}
