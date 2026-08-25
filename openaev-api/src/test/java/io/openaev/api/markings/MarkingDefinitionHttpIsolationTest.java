package io.openaev.api.markings;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.MarkingDefinition;
import io.openaev.database.repository.MarkingDefinitionRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.MarkingDefinitionFixture;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * End-to-end proof that, with {@code marking_definitions} activated, the tenant scope isolates the
 * table through the real {@link MarkingDefinitionApi} endpoints.
 *
 * <p><b>Header route only.</b> {@link MarkingDefinitionApi} is mapped on {@code
 * /api/marking-definitions} and has no {@code /api/tenants/{tenantId}/...} route, so the selector
 * is always {@code X-Tenant-Ids}. Generating path-route cases would 404 for the wrong reason and
 * produce fake-green negatives (see TENANT_ISOLATION.md, "Red and green"). Unlike the pilot, the
 * header route is legitimately testable here: {@link MarkingDefinition} carries no v1 Hibernate
 * {@code @Filter} whose predicate would contradict v2's.
 *
 * <p>Each test stays on a single tenant selector: the per-request scope is set once per transaction
 * and {@code TenantScopeTransactionAspect} refuses to redefine it.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=marking_definitions")
@WithMockUser(isAdmin = true)
// @CoversTenantIsolation("marking_definitions") // uncomment when the tenant-scope-coverage CI gate
// lands
@DisplayName("marking_definitions read and write isolation through the real HTTP endpoint")
class MarkingDefinitionHttpIsolationTest extends IntegrationTest {

  private static final String MARKING_URI = "/api/marking-definitions";
  private static final String MARKING_BY_ID = MARKING_URI + "/{markingId}";
  private static final String TENANT_IDS_HEADER = "X-Tenant-Ids";

  private static final String NAME_A = "ISO:MARKING-A";
  private static final String NAME_B = "ISO:MARKING-B";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private MarkingDefinitionRepository markingDefinitionRepository;

  private String tenantA;
  private String tenantB;
  private String markingA;
  private String markingB;

  @BeforeEach
  void seedTwoTenantsWithOneMarkingEach() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("marking-iso-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("marking-iso-b").getId();
    // Native inserts, not API creates: two MockMvc creates would set the tenant scope twice in one
    // transaction and TenantScopeTransactionAspect throws. Native inserts set no scope at all.
    // Note each tenant ALSO gets the nine defaults from MarkingDefinitionDependenciesManager at
    // creation; every assertion below keys off these two ids, never off a row count.
    markingA = seedMarking(tenantA, NAME_A);
    markingB = seedMarking(tenantB, NAME_B);
  }

  // -- READ --

  @Test
  @DisplayName("under tenant A's scope: A's marking is visible, B's is hidden")
  void given_tenantAScope_should_readOwnMarkingAndHideTenantBs() throws Exception {
    // -- ACT & ASSERT --
    mvc.perform(get(MARKING_BY_ID, markingA).header(TENANT_IDS_HEADER, tenantA))
        .andExpect(status().isOk());
    mvc.perform(get(MARKING_BY_ID, markingB).header(TENANT_IDS_HEADER, tenantA))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("under tenant B's scope: B's marking is visible, A's is hidden")
  void given_tenantBScope_should_readOwnMarkingAndHideTenantAs() throws Exception {
    // -- ACT & ASSERT --
    mvc.perform(get(MARKING_BY_ID, markingB).header(TENANT_IDS_HEADER, tenantB))
        .andExpect(status().isOk());
    mvc.perform(get(MARKING_BY_ID, markingA).header(TENANT_IDS_HEADER, tenantB))
        .andExpect(status().isNotFound());
  }

  // -- SEARCH --

  @Test
  @DisplayName("via the X-Tenant-Ids header: search returns A's marking and not B's")
  void given_tenantAScope_should_scopeSearchToTenantA() throws Exception {
    // -- ARRANGE --
    String body = asJsonString(PaginationFixture.getDefault().textSearch("").size(200).build());

    // -- ACT --
    String response =
        mvc.perform(
                post(MARKING_URI + "/search")
                    .header(TENANT_IDS_HEADER, tenantA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    assertTrue(
        response.contains(markingA), "A's marking must appear when A is selected via header");
    assertFalse(response.contains(markingB), "B's marking must not appear");
  }

  // -- FAIL-CLOSED --

  @Test
  @DisplayName("no scope set: the repository read is empty although the row exists")
  void given_noScopeInTransaction_should_returnNoRows() {
    // No TxCtx resolved in this transaction (no MockMvc call), so app.current_tenants was never
    // set and can_access_tenant denies every row. Fail-closed, not fail-open: this is what
    // protects the table if an endpoint ever loses its TxCtx parameter.
    // -- ACT & ASSERT --
    assertEquals(0L, markingDefinitionRepository.count(), "a scope-less read must see no rows");
    assertEquals(1L, rawCount(markingA), "the row exists, it is only hidden");
  }

  // -- CREATE (write attribution) --

  @Test
  @DisplayName("a create under tenant A's scope is attributed to tenant A")
  void given_tenantAScope_should_attributeCreateToTenantA() throws Exception {
    // -- ARRANGE --
    var input = MarkingDefinitionFixture.createInputWithName("ISO:CREATED-UNDER-A");

    // -- ACT --
    String response =
        mvc.perform(
                post(MARKING_URI)
                    .header(TENANT_IDS_HEADER, tenantA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(input))
                    .with(csrf()))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    String createdId = JsonPath.read(response, "$.marking_id");
    // Raw JDBC, not an entityManager native query: the inspector rewrites the latter, so its
    // result would flip with the transaction's scope.
    assertEquals(tenantA, rawTenant(createdId), "the created marking must belong to tenant A");
  }

  @Test
  @DisplayName("a create with no tenant selector is refused (a single-tenant scope is required)")
  void given_noTenantSelector_should_rejectCreate() throws Exception {
    // The mock user belongs to both seeded tenants, so an absent selector resolves to a
    // multi-tenant scope; TenantWriteScopeResolver cannot attribute the row and refuses with 400.
    // -- ACT & ASSERT --
    mvc.perform(
            post(MARKING_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    asJsonString(MarkingDefinitionFixture.createInputWithName("ISO:NO-SELECTOR")))
                .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  // -- UPDATE --

  @Test
  @DisplayName("under tenant A's scope: A can update its own marking")
  void given_tenantAScope_should_updateOwnMarking() throws Exception {
    // -- ARRANGE --
    var update = MarkingDefinitionFixture.createInputWithName("ISO:RENAMED-A");

    // -- ACT --
    mvc.perform(
            put(MARKING_BY_ID, markingA)
                .header(TENANT_IDS_HEADER, tenantA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(update))
                .with(csrf()))
        .andExpect(status().isOk());

    // -- ASSERT --
    assertEquals("ISO:RENAMED-A", rawName(markingA), "A's own marking must be updated");
  }

  @Test
  @DisplayName("under tenant A's scope: updating B's marking is not found and leaves it untouched")
  void given_tenantAScope_should_notUpdateTenantBsMarking() throws Exception {
    // -- ARRANGE --
    var update = MarkingDefinitionFixture.createInputWithName("ISO:HIJACKED");

    // -- ACT --
    mvc.perform(
            put(MARKING_BY_ID, markingB)
                .header(TENANT_IDS_HEADER, tenantA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(update))
                .with(csrf()))
        .andExpect(status().isNotFound());

    // -- ASSERT --
    assertEquals(NAME_B, rawName(markingB), "B's marking must be untouched by tenant A");
  }

  // -- DELETE --

  @Test
  @DisplayName("under tenant A's scope: A can delete its own marking")
  void given_tenantAScope_should_deleteOwnMarking() throws Exception {
    // -- ACT --
    mvc.perform(delete(MARKING_BY_ID, markingA).header(TENANT_IDS_HEADER, tenantA).with(csrf()))
        .andExpect(status().isNoContent());

    // -- ASSERT --
    assertEquals(0L, rawCount(markingA), "A's own marking must be deleted");
  }

  @Test
  @DisplayName(
      "under tenant A's scope: deleting B's marking does not happen and leaves it in place")
  void given_tenantAScope_should_notDeleteTenantBsMarking() throws Exception {
    // DEVIATION from the skill's "cross-tenant DELETE is a 2xx no-op": that semantic assumes the
    // DELETE statement reaches the DB and the inspector narrows its WHERE. MarkingDefinitionService
    // .delete() calls getOrThrow(id) FIRST, so the scoped lookup misses and the request 404s
    // exactly like the cross-tenant PUT. The security property is identical and is what the ground
    // truth below actually pins: B's row survives.
    // -- ACT --
    mvc.perform(delete(MARKING_BY_ID, markingB).header(TENANT_IDS_HEADER, tenantA).with(csrf()))
        .andExpect(status().isNotFound());

    // -- ASSERT --
    assertEquals(1L, rawCount(markingB), "B's marking must survive tenant A's delete attempt");
  }

  // -- SEED --

  // Native insert with an explicit tenant_id: the setup seeds TWO tenants, and two API creates
  // would set the tenant scope twice in one transaction, which TenantScopeTransactionAspect
  // rejects. marking_id is VARCHAR, so no CAST(? AS uuid) is needed anywhere in this file.
  private String seedMarking(String tenantId, String name) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO marking_definitions"
                + " (marking_id, marking_type, marking_name, marking_order, marking_color, tenant_id)"
                + " VALUES (:id, :type, :name, :order, :color, :tenant)")
        .setParameter("id", id)
        .setParameter("type", MarkingDefinition.TYPE_TLP)
        .setParameter("name", name)
        .setParameter("order", MarkingDefinitionFixture.DEFAULT_ORDER)
        .setParameter("color", MarkingDefinitionFixture.DEFAULT_COLOR)
        .setParameter("tenant", tenantId)
        .executeUpdate();
    return id;
  }

  // -- GROUND TRUTH --

  // Raw JDBC on the test's own connection: it sees the uncommitted seed and the rewriter does not
  // touch a statement it never generated. A flush first forces any pending scoped UPDATE/DELETE to
  // reach the database.
  private String rawName(String markingId) {
    return rawString(
        "SELECT marking_name FROM marking_definitions WHERE marking_id = ?", markingId);
  }

  private String rawTenant(String markingId) {
    return rawString("SELECT tenant_id FROM marking_definitions WHERE marking_id = ?", markingId);
  }

  private String rawString(String sql, String markingId) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, markingId);
                try (ResultSet rows = statement.executeQuery()) {
                  return rows.next() ? rows.getString(1) : null;
                }
              }
            });
  }

  private long rawCount(String markingId) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement statement =
                  connection.prepareStatement(
                      "SELECT count(*) FROM marking_definitions WHERE marking_id = ?")) {
                statement.setString(1, markingId);
                try (ResultSet rows = statement.executeQuery()) {
                  rows.next();
                  return rows.getLong(1);
                }
              }
            });
  }
}
