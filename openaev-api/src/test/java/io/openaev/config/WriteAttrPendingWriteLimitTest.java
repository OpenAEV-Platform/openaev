package io.openaev.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.api.groups.TenantGroupApi;
import io.openaev.context.TenantContext;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Known limit of a SQL-layer detector, pinned: a write a request asks for in a
 * {@code @Transactional} test that never autoflushes is rolled back with the test transaction and
 * never reaches SQL, so the trigger never fires and the gate cannot see it. The loss is the
 * rollback itself, which flushes nothing, not the moment the recorder stops: the recorder is still
 * on when the test body ends. The only way to see such a write would be a forced flush before the
 * rollback, which the detector does not do on purpose: a detector run must not change what the
 * suite executes.
 *
 * <p>The second test fails the day that limit is closed by accident: if anything flushes the
 * pending insert before the gate reads its violations, the gate keys the group write and the
 * assertion on what it keyed for the first test goes red.
 */
@Transactional
@Import(WriteAttrDetectorTestConfig.class)
@WithMockUser(isAdmin = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName(
    "Write-attribution: a request write left pending at rollback is invisible (known limit)")
class WriteAttrPendingWriteLimitTest extends IntegrationTest {

  private static final String CONTROLLER = "io.openaev.api.groups.TenantGroupApi.createGroup";
  private static String pendingGroupId;

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantB;

  @BeforeEach
  void setUp() throws Exception {
    tenantB = tenantHelper.createTenantWithCurrentUser("wattr-pending-b").getId();
    entityManager
        .unwrap(Session.class)
        .doWork(c -> WriteAttrDetectorTrigger.install(c, TenantTables.selfIsolatedTables()));
  }

  @AfterEach
  void clear() {
    TenantContext.clearCurrentTenant();
  }

  @Test
  @Order(1)
  @DisplayName(
      "Given a request write that never autoflushes, should reach no SQL while the recorder is on")
  void given_requestWriteLeftPending_should_reachNoSqlWhileRecording() throws Exception {
    // Arrange
    TenantContext.clearCurrentTenant();
    WriteAttrDetectorRecorder.start();

    // Act: the header-route create asks for a wrong-tenant insert that stays pending.
    pendingGroupId = postGroup();
    // Read through the transaction's JDBC connection, not Hibernate, so nothing autoflushes.
    Long rows =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM groups WHERE group_id = ?", Long.class, pendingGroupId);

    // Assert
    assertEquals(0L, rows, "the insert is still pending in the persistence context");
    assertTrue(
        WriteAttrDetectorRecorder.isRecording(),
        "the recorder is on when the body ends: what loses the write is the rollback, not the stop");
    assertTrue(
        WriteAttrDetectorRecorder.violations().stream().noneMatch(v -> "groups".equals(v.table())),
        "nothing reached the trigger, so nothing can be recorded");
  }

  @Test
  @Order(2)
  @DisplayName(
      "Given the previous test rolled back, should have keyed nothing for its pending write")
  void given_previousTestRolledBack_should_haveKeyedNothing() {
    assumeTrue(
        WriteAttrGateExtension.isEnabled(), "the gate keys writes only under the detector flag");
    // Like every detector test: drop what the fixtures wrote before the body, so the gate closes
    // this test on its own writes only.
    WriteAttrDetectorRecorder.start();

    // Assert: the gate closed the previous test without seeing the pending insert.
    assertTrue(
        WriteAttrGateExtension.lastKeyed().stream()
            .noneMatch(sig -> sig.equals("groups DEFAULT " + CONTROLLER)),
        "a pending write flushed before the gate read its violations would close a documented"
            + " limit by accident; keyed="
            + WriteAttrGateExtension.lastKeyed());
    Long rows =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM groups WHERE group_id = ?", Long.class, pendingGroupId);
    assertEquals(0L, rows, "the pending insert was rolled back with the previous test");
  }

  private String postGroup() throws Exception {
    String response =
        mvc.perform(
                post(TenantGroupApi.GROUP_URI)
                    .header("X-Tenant-Ids", tenantB)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"group_name\":\"wattr-pending\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(response, "$.group_id");
  }
}
