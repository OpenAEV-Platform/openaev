package io.openaev.rest.executor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The data isolation must not depend on the caller being an administrator. The other isolation
 * tests run as admin (RBAC bypassed); this one runs as a non-admin that is a member of two tenants
 * and holds only the capability needed to read executors, and shows the rewriter still returns one
 * tenant's executors under its path. It closes the gap left by the admin-only tests: that the
 * scope, set from the request and never from the isAdmin flag, is what isolates a non-admin too.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=executors")
@WithMockUser(isAdmin = false)
@DisplayName("executors isolation holds for a non-admin spanning two tenants")
class ExecutorNonAdminIsolationTest extends IntegrationTest {

  private static final Set<Capability> READ_EXECUTORS = Set.of(Capability.ACCESS_ASSETS);

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantA;
  private String executorA;
  private String executorB;

  @BeforeEach
  void seedTwoTenantsTheNonAdminBelongsToWithOneExecutorEach() throws Exception {
    tenantA = tenantHelper.createTenantWithCapabilities("nonadmin-iso-a", READ_EXECUTORS).getId();
    String tenantB =
        tenantHelper.createTenantWithCapabilities("nonadmin-iso-b", READ_EXECUTORS).getId();
    executorA = seedExecutor(tenantA, "nonadmin-executor-a", "type-a");
    executorB = seedExecutor(tenantB, "nonadmin-executor-b", "type-b");
  }

  @Test
  @DisplayName("a non-admin listing under tenant A's path sees only A's executor")
  void listUnderTenantAReturnsOnlyAForNonAdmin() throws Exception {
    String response =
        mvc.perform(get("/api/tenants/{tenantId}/executors", tenantA))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTrue(
        response.contains(executorA), "A's executor must appear for the non-admin member of A");
    assertFalse(response.contains(executorB), "B's executor must not leak to A's scope");
  }

  private String seedExecutor(String tenantId, String name, String type) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO executors (executor_id, tenant_id, executor_name, executor_type,"
                + " executor_external, executor_created_at, executor_updated_at)"
                + " VALUES (:id, :tenant, :name, :type, false, now(), now())")
        .setParameter("id", id)
        .setParameter("tenant", tenantId)
        .setParameter("name", name)
        .setParameter("type", type)
        .executeUpdate();
    return id;
  }
}
