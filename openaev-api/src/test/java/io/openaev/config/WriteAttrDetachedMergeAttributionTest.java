package io.openaev.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.api.groups.TenantGroupApi;
import io.openaev.config.WriteAttrDetectorRecorder.Violation;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.Set;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * A write asked for through {@code merge} of a transient instance with an assigned id (the shape of
 * {@code repository.save(entity)} when the id is set before saving) inserts a managed copy, not the
 * instance the application handed to Hibernate. The ask-time capture is keyed by instance identity,
 * so if it keeps only the original, the copy that reaches {@code pre-insert} matches nothing and
 * the write falls back to the flush stack. Flushed from a test frame, it is then waived as
 * test-driven: a real production wrong-tenant write dropped in silence.
 *
 * <p>The production shape is the tenant group create on the non-prefixed route: the service assigns
 * the id, takes the tenant from the ambient context (the default tenant, since the header route
 * does not set it) and saves, while the request scope is tenant B.
 */
@Transactional
@Import(WriteAttrDetectorTestConfig.class)
@WithMockUser(isAdmin = true)
@DisplayName("Write-attribution: a merged transient entity is attributed to the asking request")
class WriteAttrDetachedMergeAttributionTest extends IntegrationTest {

  private static final String DEFAULT_TENANT = Tenant.DEFAULT_TENANT_UUID;
  private static final String CONTROLLER = "io.openaev.api.groups.TenantGroupApi.createGroup";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String tenantB;

  @BeforeEach
  void setUp() throws Exception {
    tenantB = tenantHelper.createTenantWithCurrentUser("wattr-merge-b").getId();
    entityManager
        .unwrap(Session.class)
        .doWork(c -> WriteAttrDetectorTrigger.install(c, TenantTables.selfIsolatedTables()));
  }

  @AfterEach
  void clear() {
    TenantContext.clearCurrentTenant();
    WriteAttrDetectorRecorder.stop();
  }

  @Nested
  @DisplayName("Given a header-route create that saves a transient entity with an assigned id")
  class MergedTransientEntity {

    @Test
    @DisplayName(
        "Given the insert flushed later from a test frame, should attribute it to the controller")
    void given_mergedCreateFlushedFromTestFrame_should_attributeToTheController() throws Exception {
      // Arrange
      TenantContext.clearCurrentTenant();
      assertEquals(
          DEFAULT_TENANT,
          TenantContext.getCurrentTenant(),
          "precondition: the ambient tenant falls back to the default on the header route");
      WriteAttrDetectorRecorder.start();

      // Act
      String id = postGroup();
      // No explicit flush: the pending insert of the managed copy is forced out by a native read
      // from this test method, so the flush stack holds no production frame.
      String writtenTenant = readTenantFromTestFrame(id);
      WriteAttrDetectorRecorder.stop();

      // Assert
      assertEquals(
          DEFAULT_TENANT,
          writtenTenant,
          "precondition: the header-route create stamps the row with the default tenant");
      List<Violation> groupViolations =
          WriteAttrDetectorRecorder.violations().stream()
              .filter(v -> "groups".equals(v.table()))
              .toList();
      assertFalse(groupViolations.isEmpty(), "the trigger must see the group write");
      List<String> attributed =
          WriteAttrGateExtension.offendingSignatures(
              WriteAttrDetectorRecorder.violations(), Set.of());
      assertTrue(
          attributed.contains("groups DEFAULT " + CONTROLLER),
          "the merged copy must be attributed to the controller, not waived as test-driven;"
              + " attributed="
              + attributed
              + " violations="
              + groupViolations);
    }
  }

  private String postGroup() throws Exception {
    String response =
        mvc.perform(
                post(TenantGroupApi.GROUP_URI)
                    .header("X-Tenant-Ids", tenantB)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"group_name\":\"wattr-merge\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(response, "$.group_id");
  }

  /**
   * Forces the pending flush from this test method with a native query the inspector leaves alone
   * (no table), so the flush stack holds no production frame, then reads the row through the
   * transaction's JDBC connection, which the statement inspector does not rewrite: under the
   * production active-tables list a scoped native read of an active table would not see a row of
   * another tenant, which is exactly the row this test wrote on purpose.
   */
  private String readTenantFromTestFrame(String groupId) {
    entityManager.createNativeQuery("SELECT 1").getSingleResult();
    return jdbcTemplate.queryForObject(
        "SELECT tenant_id FROM groups WHERE group_id = ?", String.class, groupId);
  }
}
