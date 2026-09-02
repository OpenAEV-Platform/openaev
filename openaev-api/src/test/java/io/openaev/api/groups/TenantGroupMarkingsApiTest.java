package io.openaev.api.groups;

import static io.openaev.api.groups.TenantGroupApi.TENANT_GROUP_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.MarkingDefinitionFixture.createMarkingDefinition;
import static io.openaev.utils.fixtures.MarkingDefinitionFixture.uniqueName;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.api.groups.dto.GroupUpdateMarkingsInput;
import io.openaev.config.cache.MarkingClearanceCacheManager;
import io.openaev.context.MarkingCtx;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Group;
import io.openaev.database.model.MarkingDefinition;
import io.openaev.database.model.User;
import io.openaev.database.repository.GroupRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.TenantGroupFixture;
import io.openaev.utils.fixtures.composers.MarkingDefinitionComposer;
import io.openaev.utils.fixtures.composers.TenantGroupComposer;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code PUT /api/groups/{id}/markings} — the write path that gives a user a clearance.
 *
 * <p>This is the endpoint the marking PoC was blocked on: until it existed, {@code groups_markings}
 * had no writer, so every user resolved to {@link MarkingCtx#none()} and the clearance path could
 * only be exercised against a stubbed {@code JdbcTemplate}. Here it runs against real rows.
 *
 * <p>Assertions are <b>containment</b>-based, never equality: every tenant is seeded with nine
 * default markings (TLP:CLEAR..RED, PAP:CLEAR..RED), and ordinality expansion legitimately pulls
 * the seeded low orders into any clearance. Asserting an exact set would be asserting the seed.
 *
 * <p>Also pins the {@code TenantGroupService} entry in {@code TenantActiveTableAccessArchTest}: the
 * cross-tenant case below is what makes "the inspector scopes this read" checkable rather than a
 * claim in a comment.
 */
@TestInstance(PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Tenant group markings API")
class TenantGroupMarkingsApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private GroupRepository groupRepository;
  @Autowired private TenantGroupComposer tenantGroupComposer;
  @Autowired private MarkingDefinitionComposer markingDefinitionComposer;
  @Autowired private MarkingClearanceCacheManager clearanceCache;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @PersistenceContext private EntityManager entityManager;

  private String tenantId;
  private String userId;
  private Group group;
  private MarkingDefinition green;
  private MarkingDefinition red;

  @BeforeEach
  void setUp() {
    tenantGroupComposer.reset();
    markingDefinitionComposer.reset();
    tenantId = TenantContext.getCurrentTenant();
    User user = testUserHolder.get();
    userId = user.getId();
    tenantRepository.addUserToTenant(userId, tenantId);
    tenantMembershipCacheManager.evict(userId, tenantId);

    // Orders above the seeded 10..50 band so a fixture never ties with a default, and RED above
    // GREEN so the ordinality expansion below is unambiguous.
    green = persistedMarking(60);
    red = persistedMarking(70);

    group =
        tenantGroupComposer
            .forGroup(TenantGroupFixture.getGroup("markings-" + uniqueName()))
            .persist()
            .get();
    // The caller must be a member: a clearance is what a group grants ITS MEMBERS.
    group.setUsers(new ArrayList<>(List.of(user)));
    groupRepository.save(group);
    clearanceCache.evictForUser(userId);
  }

  @Nested
  @DisplayName("granting a clearance")
  class Granting {

    @Test
    @DisplayName("given a group the user belongs to, should grant exactly that marking")
    void given_groupTheUserBelongsTo_should_grantThatMarking() throws Exception {
      // -- ACT --
      assignMarkings(List.of(green.getId())).andExpect(status().is2xxSuccessful());

      // -- ASSERT --
      // Flow 1: a member of a group marked GREEN holds GREEN — and not RED.
      Set<String> clearance = clearanceOfMember();
      assertTrue(clearance.contains(green.getId()), "GREEN should be held: " + clearance);
      assertFalse(clearance.contains(red.getId()), "RED must not be held: " + clearance);
    }

    @Test
    @DisplayName("given a higher marking, should grant the lower ones it implies")
    void given_higherMarking_should_grantTheImpliedLowerOnes() throws Exception {
      // -- ACT --
      assignMarkings(List.of(red.getId())).andExpect(status().is2xxSuccessful());

      // -- ASSERT --
      // Ordinality is resolved in Java, not SQL: granting RED alone must already yield a FLAT set
      // containing GREEN, because the database predicate is plain set containment and knows nothing
      // about order.
      Set<String> clearance = clearanceOfMember();
      assertTrue(clearance.contains(red.getId()), "RED should be held: " + clearance);
      assertTrue(clearance.contains(green.getId()), "RED must imply GREEN: " + clearance);
    }

    @Test
    @DisplayName("given a re-assignment, should reflect it immediately — the cache is evicted")
    void given_reassignment_should_reflectItImmediately() throws Exception {
      // -- ARRANGE --
      assignMarkings(List.of(green.getId())).andExpect(status().is2xxSuccessful());
      // Warm the cache deliberately: without eviction the next read would serve this value.
      assertFalse(clearanceOfMember().contains(red.getId()));

      // -- ACT --
      assignMarkings(List.of(red.getId())).andExpect(status().is2xxSuccessful());

      // -- ASSERT --
      // Flow 3: an admin raises the group to RED and the member sees RED on their next request,
      // rather than after the 5-minute TTL.
      assertTrue(clearanceOfMember().contains(red.getId()), "eviction did not happen");
    }

    @Test
    @DisplayName("given an empty list, should revoke every marking")
    void given_emptyList_should_revokeEverything() throws Exception {
      // -- ARRANGE --
      assignMarkings(List.of(red.getId())).andExpect(status().is2xxSuccessful());
      assertTrue(clearanceOfMember().contains(red.getId()));

      // -- ACT --
      assignMarkings(List.of()).andExpect(status().is2xxSuccessful());

      // -- ASSERT --
      // 🔴 The direction that fails OPEN if eviction is missed: a revoked grant that stays cached
      // keeps granting access, because the cached set is never re-checked against the table.
      Set<String> clearance = clearanceOfMember();
      assertFalse(clearance.contains(red.getId()), "revoked RED still held: " + clearance);
      assertFalse(clearance.contains(green.getId()), "revoked GREEN still held: " + clearance);
      assertTrue(reloadedGroup().getMarkings().isEmpty());
    }
  }

  @Nested
  @DisplayName("guards")
  class Guards {

    @Test
    @DisplayName("given an unknown marking, should 404 rather than assign the rest")
    void given_unknownMarking_should_notFound() throws Exception {
      // -- ACT --
      assignMarkings(List.of(green.getId(), "does-not-exist")).andExpect(status().isNotFound());

      // -- ASSERT --
      // A partial assignment is the dangerous outcome: the caller believes they granted two
      // markings and only one was audited.
      assertTrue(reloadedGroup().getMarkings().isEmpty());
    }

    @Test
    @DisplayName("given a marking from another tenant, should refuse to assign it")
    void given_markingFromAnotherTenant_should_refuse() throws Exception {
      // -- ARRANGE --
      String otherTenantId = tenantHelper.createTenant("marking-assign-other").getId();
      MarkingDefinition foreign =
          markingDefinitionComposer
              .forMarkingDefinition(
                  createMarkingDefinition(MarkingDefinition.TYPE_TLP, uniqueName(), 60, "#111111"))
              .withTenantId(otherTenantId)
              .persist()
              .get();

      // -- ACT --
      assignMarkings(List.of(foreign.getId())).andExpect(status().isForbidden());

      // -- ASSERT --
      // 🔴 403, not 404, and the difference is the point. Two independent guards cover this:
      //
      //   1. the statement inspector, because marking_definitions is tenant-active — it would hide
      //      the row and the size check would 404;
      //   2. the escalation guard, because a clearance is per tenant, so nobody — admin included —
      //      holds another tenant's marking.
      //
      // Guard 1 does NOT fire here: the composer persisted this entity into the same persistence
      // context, so findAllById is served from Hibernate's first-level cache and never reaches SQL.
      // An inspector cannot rewrite a query that is not issued. That is a real property of the
      // mechanism worth stating in a test rather than discovering in production, and it is exactly
      // why the escalation guard is not redundant with tenant isolation.
      assertTrue(reloadedGroup().getMarkings().isEmpty());
    }
  }

  // -- HELPERS --

  private ResultActions assignMarkings(List<String> markingIds) throws Exception {
    return mvc.perform(
        put(tenantUri(TENANT_GROUP_URI) + "/" + group.getId() + "/markings")
            .content(asJsonString(new GroupUpdateMarkingsInput(markingIds)))
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .with(csrf()));
  }

  /**
   * The member's own clearance, resolved WITHOUT bypass: the caller performing the assignment is an
   * admin and would otherwise hold the whole tenant scale, which would make every assertion above
   * pass for the wrong reason.
   */
  private Set<String> clearanceOfMember() {
    // The clearance read is raw JDBC (it must not open a Hibernate session — see
    // MarkingClearanceCacheManager). It joins this test's transaction, but Hibernate has not
    // written the groups_markings rows yet: nothing commits inside a rolled-back test. Without this
    // flush the query would correctly return zero rows and the test would be measuring the flush.
    entityManager.flush();
    MarkingCtx ctx = clearanceCache.findClearance(userId, tenantId, false);
    return ctx instanceof MarkingCtx.Restricted restricted
        ? Set.copyOf(restricted.markingIds())
        : Set.of();
  }

  private Group reloadedGroup() {
    return groupRepository.findById(group.getId()).orElseThrow();
  }

  private MarkingDefinition persistedMarking(int order) {
    return markingDefinitionComposer
        .forMarkingDefinition(
            createMarkingDefinition(MarkingDefinition.TYPE_TLP, uniqueName(), order, "#c62828"))
        .withTenantId(tenantId)
        .persist()
        .get();
  }
}
