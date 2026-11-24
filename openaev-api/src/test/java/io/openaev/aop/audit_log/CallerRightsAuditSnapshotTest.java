package io.openaev.aop.audit_log;

import static io.openaev.api.groups.TenantGroupApi.GROUP_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.api.groups.dto.TenantGroupCreateInput;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.database.audit.AuditLogContext;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Tenant;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.service.account.CallerRightsInTenant;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.TestUserHolder;
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.Set;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The audit diff of a group a request updates compares the group as the request read it with the
 * group as it writes it. On the plain route whose {@code X-Tenant-Ids} header names a tenant other
 * than the default one, the permission check loads the caller again, with its groups, in the
 * default tenant ({@link CallerRightsInTenant}). That load reads none of the roles of the tenant
 * the header names, and it must not take the place of the request's own reading of the group.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@TestPropertySource(properties = {"openaev.audit-logs.transports=console"})
@DisplayName("Audit snapshots of a request whose permission check loads the caller again")
class CallerRightsAuditSnapshotTest extends IntegrationTest {

  private static final String TENANT_HEADER = "X-Tenant-Ids";
  private static final Set<Capability> GROUP_RIGHTS =
      Set.of(
          Capability.ACCESS_TENANT_USERS_GROUPS_AND_ROLES,
          Capability.MANAGE_TENANT_USERS_GROUPS_AND_ROLES);

  @DynamicPropertySource
  static void activeTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TestUserHolder testUserHolder;
  @MockitoBean private EnterpriseEditionService enterpriseEditionService;
  @MockitoSpyBean private AuditLogger auditLogger;
  @MockitoSpyBean private CallerRightsInTenant callerRightsInTenant;

  @BeforeEach
  void enableAuditLogging() {
    Mockito.when(enterpriseEditionService.isLicenseActive(any())).thenReturn(true);
    assertThat(auditLogger.isAuditLoggingEnabled()).isTrue();
  }

  @Nested
  @WithMockUser(
      withCapabilities = {
        Capability.ACCESS_TENANT_USERS_GROUPS_AND_ROLES,
        Capability.MANAGE_TENANT_USERS_GROUPS_AND_ROLES
      })
  @DisplayName("a caller without the admin flag, member of a group of A that holds a role of A")
  class MemberOfAGroupOfA {

    @Test
    @DisplayName("given_anUpdateOfTheGroupSelectingAByHeader_should_diffTheGroupAsTheRequestReadIt")
    void given_anUpdateOfTheGroupSelectingAByHeader_should_diffTheGroupAsTheRequestReadIt()
        throws Exception {
      // Arrange
      String tenantA = tenantHelper.createTenantWithCurrentUser("audit-snapshot-a").getId();
      tenantHelper.grantCapabilitiesInTenant(tenantA, GROUP_RIGHTS);
      String groupOfA = rawGroupOfCallerIn(tenantA);
      String roleOfA = rawRoleOf(groupOfA);
      TenantGroupCreateInput input = new TenantGroupCreateInput();
      input.setName("audit-snapshot-renamed");
      TenantContext.clearCurrentTenant();
      assertEquals(Tenant.DEFAULT_TENANT_UUID, TenantContext.getCurrentTenant());
      // A request starts with an empty persistence context: it reads the group itself.
      entityManager.clear();

      // Act
      mvc.perform(
              put(GROUP_URI + "/{groupId}/information", groupOfA)
                  .header(TENANT_HEADER, tenantA)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .with(csrf()))
          .andExpect(status().isOk());

      // Assert
      verify(callerRightsInTenant).loadInAmbientTenant(any());
      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, AuditLogContext.EntitySnapshot>> snapshots =
          ArgumentCaptor.forClass(Map.class);
      verify(auditLogger, timeout(5000))
          .logAccessControlEvent(any(), any(), any(), any(), any(), any(), snapshots.capture());
      AuditLogContext.EntitySnapshot snapshot = snapshots.getValue().get(groupOfA);
      assertThat(snapshot).isNotNull();
      assertThat(snapshot.operation()).isEqualTo("update");
      assertThat(snapshot.after().get("group_name")).isEqualTo("audit-snapshot-renamed");
      assertThat(snapshot.after().get("group_roles").toString()).contains(roleOfA);
      assertThat(snapshot.before().get("group_roles"))
          .as("the roles of the group as the request read it")
          .isEqualTo(snapshot.after().get("group_roles"));
    }
  }

  private String rawGroupOfCallerIn(String tenantId) {
    return rawString(
        "SELECT g.group_id FROM groups g JOIN users_groups ug ON ug.group_id = g.group_id"
            + " WHERE g.tenant_id = ? AND ug.user_id = ?",
        tenantId,
        testUserHolder.get().getId());
  }

  private String rawRoleOf(String groupId) {
    return rawString("SELECT role_id FROM groups_roles WHERE group_id = ?", groupId);
  }

  private String rawString(String sql, String... arguments) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int i = 0; i < arguments.length; i++) {
                  statement.setString(i + 1, arguments[i]);
                }
                try (ResultSet rs = statement.executeQuery()) {
                  return rs.next() ? rs.getString(1) : null;
                }
              }
            });
  }
}
