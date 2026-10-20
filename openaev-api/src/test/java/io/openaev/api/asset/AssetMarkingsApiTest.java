package io.openaev.api.asset;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.MarkingDefinitionFixture.createMarkingDefinition;
import static io.openaev.utils.fixtures.MarkingDefinitionFixture.uniqueDefinition;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.api.asset.dto.AssetBulkReplaceMarkingsInput;
import io.openaev.api.asset.dto.AssetUpdateMarkingsInput;
import io.openaev.config.cache.MarkingClearanceCacheManager;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Group;
import io.openaev.database.model.MarkingDefinition;
import io.openaev.database.model.User;
import io.openaev.database.repository.GroupRepository;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.TenantGroupFixture;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.MarkingDefinitionComposer;
import io.openaev.utils.fixtures.composers.TenantGroupComposer;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code PUT /api/assets/{id}/markings} — design step 3.3, the write path that puts a label on a
 * row.
 *
 * <p>The caller is deliberately <b>not</b> an admin: an admin resolves to the whole tenant scale
 * and would pass the escalation guard for the wrong reason, making every negative case below
 * vacuous.
 *
 * <p>Assertions read {@code marking_ids} with raw JDBC rather than through the repository. After
 * the PUT the entity is in the persistence context, so a repository read is served from Hibernate's
 * first-level cache and would happily confirm whatever the service put there in memory. JDBC is the
 * ground truth of what actually reached the column.
 */
@Transactional
@WithMockUser(
    withCapabilities = {
      Capability.MANAGE_ASSETS,
      Capability.ASSIGN_MARKING,
      Capability.DELETE_MARKING_ASSIGNMENT
    })
@DisplayName("Asset markings API")
class AssetMarkingsApiTest extends IntegrationTest {

  private static final String ASSET_URI = "/api/assets";

  @Autowired private MockMvc mvc;
  @Autowired private DataSource dataSource;
  @Autowired private GroupRepository groupRepository;
  @Autowired private TenantGroupComposer tenantGroupComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private MarkingDefinitionComposer markingDefinitionComposer;
  @Autowired private MarkingClearanceCacheManager clearanceCache;
  @PersistenceContext private EntityManager entityManager;

  private JdbcTemplate jdbc;
  private String tenantId;
  private String userId;

  private MarkingDefinition green;
  private MarkingDefinition red;
  private MarkingDefinition yellow;
  private Endpoint asset;

  @BeforeEach
  void setUp() {
    jdbc = new JdbcTemplate(dataSource);
    tenantGroupComposer.reset();
    endpointComposer.reset();
    markingDefinitionComposer.reset();

    tenantId = TenantContext.getCurrentTenant();
    User user = testUserHolder.get();
    userId = user.getId();
    tenantRepository.addUserToTenant(userId, tenantId);
    tenantMembershipCacheManager.evict(userId, tenantId);

    // Orders above the seeded 10..50 band so a fixture never ties with a default.
    green = marking(60);
    red = marking(70);
    yellow = marking(65);

    asset =
        endpointComposer
            .forEndpoint(EndpointFixture.createEndpoint("asset-markings-" + uniqueDefinition()))
            .persist()
            .get();

    Group group =
        tenantGroupComposer
            .forGroup(TenantGroupFixture.getGroup("asset-markings-" + uniqueDefinition()))
            .persist()
            .get();
    group.setUsers(new ArrayList<>(List.of(user)));
    groupRepository.save(group);

    // The rows must exist in the database, not just in the persistence context, before the raw-JDBC
    // grant below can reference them.
    entityManager.flush();

    // The caller holds GREEN and YELLOW, and nothing above them — the whole point of the negative
    // cases (RED is never granted here).
    jdbc.update(
        "INSERT INTO groups_markings (group_id, marking_id) VALUES (?, ?)",
        group.getId(),
        green.getId());
    jdbc.update(
        "INSERT INTO groups_markings (group_id, marking_id) VALUES (?, ?)",
        group.getId(),
        yellow.getId());
    clearanceCache.evictForUser(userId);
  }

  @Nested
  @DisplayName("assigning a marking")
  class Assigning {

    @Test
    @DisplayName("given a marking the caller holds, should write it to the asset")
    void given_heldMarking_should_writeIt() throws Exception {
      // -- ACT --
      assignMarkings(List.of(green.getId())).andExpect(status().is2xxSuccessful());

      // -- ASSERT --
      assertArrayEquals(new String[] {green.getId()}, storedMarkings());
    }

    @Test
    @DisplayName("given a marking the caller does not hold, should refuse with 403")
    void given_unheldMarking_should_forbid() throws Exception {
      // -- ACT / ASSERT --
      // The guard that makes marking a boundary at all: without it, anyone able to edit an asset
      // could label it RED and then read every other RED row by joining a group they control.
      assignMarkings(List.of(red.getId())).andExpect(status().isForbidden());

      // The refusal must also be a no-op, not a partial write.
      assertEquals(0, storedMarkings().length, "a refused assignment must not touch the column");
    }

    @Test
    @DisplayName("given a marking it just assigned, should leave the asset readable by the caller")
    void given_assignedMarking_should_notLockTheCallerOut() throws Exception {
      // -- ARRANGE --
      assignMarkings(List.of(green.getId())).andExpect(status().is2xxSuccessful());
      entityManager.flush();
      entityManager.clear();

      // -- ACT / ASSERT --
      // 🔴 Self-lockout is impossible BY CONSTRUCTION, and this pins that reasoning. The escalation
      // guard enforces requested ⊆ clearance, and a row is visible iff row_markings ⊆ clearance —
      // so the asset you just marked is still yours to read. There is no separate check for it, and
      // this test is what would catch the guard being loosened to allow one.
      mvc.perform(get(ASSET_URI + "/" + asset.getId())).andExpect(status().is2xxSuccessful());
    }

    @Test
    @DisplayName("given an unknown marking id, should report not found")
    void given_unknownMarking_should_notFound() throws Exception {
      // -- ACT / ASSERT --
      assignMarkings(List.of("does-not-exist")).andExpect(status().isNotFound());
    }
  }

  @Nested
  @DisplayName("removing markings")
  class Removing {

    @Test
    @DisplayName("given an empty list, should clear the markings and make the asset public again")
    void given_emptyList_should_clearMarkings() throws Exception {
      // -- ARRANGE --
      assignMarkings(List.of(green.getId())).andExpect(status().is2xxSuccessful());

      // -- ACT --
      assignMarkings(List.of()).andExpect(status().is2xxSuccessful());

      // -- ASSERT --
      // Declassification is allowed here precisely because the caller could already read the row;
      // it is logged rather than blocked. An empty set is contained in every clearance, so the
      // asset becomes visible to everyone again.
      assertEquals(0, storedMarkings().length);
    }

    @Test
    @DisplayName("given an asset marked above the caller's clearance, should not find it")
    void given_assetAboveClearance_should_notFound() throws Exception {
      // -- ARRANGE --
      // Seeded out of band: an ORM write of RED would itself be blocked, which is the guard working
      // but useless for arranging the fixture.
      entityManager.flush();
      jdbc.update(
          "UPDATE assets SET marking_ids = ? WHERE asset_id = ?",
          (Object) new String[] {red.getId()},
          asset.getId());
      entityManager.clear();

      // -- ACT / ASSERT --
      // 404 rather than 403: you cannot declassify what you cannot see, and the response must not
      // confirm that a RED asset exists at this id.
      assignMarkings(List.of()).andExpect(status().isNotFound());
    }
  }

  @Nested
  @DisplayName("bulk replacing markings")
  class BulkReplacing {

    @Test
    @DisplayName("given an asset with existing markings, should replace the whole set")
    void given_existingMarkings_should_replaceWholeSet() throws Exception {
      // -- ARRANGE --
      // Set out of band, bypassing the single-asset escalation guard, purely to arrange fixture
      // state - GREEN must not survive the replace, unlike a merge.
      entityManager.flush();
      jdbc.update(
          "UPDATE assets SET marking_ids = ? WHERE asset_id = ?",
          (Object) new String[] {green.getId()},
          asset.getId());
      entityManager.clear();

      // -- ACT --
      bulkReplaceMarkings(List.of(asset.getId()), List.of(yellow.getId()))
          .andExpect(status().is2xxSuccessful());

      // -- ASSERT --
      assertArrayEquals(new String[] {yellow.getId()}, storedMarkings());
    }

    @Test
    @DisplayName("given an empty list, should clear the markings of every selected asset")
    void given_emptyList_should_clearMarkings() throws Exception {
      // -- ARRANGE --
      entityManager.flush();
      jdbc.update(
          "UPDATE assets SET marking_ids = ? WHERE asset_id = ?",
          (Object) new String[] {green.getId()},
          asset.getId());
      entityManager.clear();

      // -- ACT --
      bulkReplaceMarkings(List.of(asset.getId()), List.of()).andExpect(status().is2xxSuccessful());

      // -- ASSERT --
      assertEquals(0, storedMarkings().length);
    }

    @Test
    @DisplayName(
        "given a marking the caller does not hold, should refuse with 403 and touch nothing")
    void given_unheldMarking_should_forbid() throws Exception {
      // -- ACT / ASSERT --
      bulkReplaceMarkings(List.of(asset.getId()), List.of(red.getId()))
          .andExpect(status().isForbidden());
      assertEquals(0, storedMarkings().length, "a refused replace must not touch the column");
    }

    @Test
    @DisplayName("given an unknown marking id, should report not found")
    void given_unknownMarking_should_notFound() throws Exception {
      // -- ACT / ASSERT --
      bulkReplaceMarkings(List.of(asset.getId()), List.of("does-not-exist"))
          .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName(
        "given the caller holds neither marking capability, should refuse an addition with 403")
    @WithMockUser(withCapabilities = {Capability.MANAGE_ASSETS})
    void given_noMarkingCapability_withAddition_should_forbid() throws Exception {
      // -- ACT / ASSERT --
      // The asset starts unmarked, so replacing with GREEN is a pure addition, gated by
      // ASSIGN_MARKING alone - mirrored from the single-asset endpoint's own diff.
      //
      // DELETE_MARKING_ASSIGNMENT is deliberately NOT granted here either: Capability.java declares
      // it as a child of ASSIGN_MARKING (DELETE_MARKING_ASSIGNMENT(ASSIGN_MARKING, ...)), and
      // Role.setCapabilities resolves parents on the way in, so holding DELETE_MARKING_ASSIGNMENT
      // without ASSIGN_MARKING is not a constructible state - granting only the former would
      // silently also grant the latter, making this test vacuous.
      bulkReplaceMarkings(List.of(asset.getId()), List.of(green.getId()))
          .andExpect(status().isForbidden());
      assertEquals(0, storedMarkings().length);
    }

    @Test
    @DisplayName(
        "given the caller holds neither marking capability, should refuse a removal with 403")
    @WithMockUser(withCapabilities = {Capability.MANAGE_ASSETS})
    void given_noMarkingCapability_withRemoval_should_forbid() throws Exception {
      // -- ARRANGE --
      entityManager.flush();
      jdbc.update(
          "UPDATE assets SET marking_ids = ? WHERE asset_id = ?",
          (Object) new String[] {green.getId()},
          asset.getId());
      entityManager.clear();

      // -- ACT / ASSERT --
      // GREEN dropped, nothing added - a pure removal, gated by DELETE_MARKING_ASSIGNMENT alone.
      bulkReplaceMarkings(List.of(asset.getId()), List.of()).andExpect(status().isForbidden());
      assertArrayEquals(new String[] {green.getId()}, storedMarkings());
    }

    @Test
    @DisplayName(
        "given the caller lacks DELETE_MARKING_ASSIGNMENT and the replace would remove a marking,"
            + " should refuse with 403")
    @WithMockUser(withCapabilities = {Capability.MANAGE_ASSETS, Capability.ASSIGN_MARKING})
    void given_missingDeleteCapability_withRemoval_should_forbid() throws Exception {
      // -- ARRANGE --
      entityManager.flush();
      jdbc.update(
          "UPDATE assets SET marking_ids = ? WHERE asset_id = ?",
          (Object) new String[] {green.getId()},
          asset.getId());
      entityManager.clear();

      // -- ACT / ASSERT --
      // GREEN dropped, nothing added - a pure removal, the direction DELETE_MARKING_ASSIGNMENT
      // gates.
      bulkReplaceMarkings(List.of(asset.getId()), List.of()).andExpect(status().isForbidden());
      assertArrayEquals(new String[] {green.getId()}, storedMarkings());
    }

    @Test
    @DisplayName(
        "given the caller lacks DELETE_MARKING_ASSIGNMENT but the replace is a pure addition,"
            + " should succeed")
    @WithMockUser(withCapabilities = {Capability.MANAGE_ASSETS, Capability.ASSIGN_MARKING})
    void given_missingDeleteCapability_withPureAddition_should_succeed() throws Exception {
      // -- ACT / ASSERT --
      // The asset starts unmarked, so replacing with GREEN is a pure addition - the aggregate
      // scope-wide diff never touches the DELETE direction, so the missing capability must not
      // block it.
      bulkReplaceMarkings(List.of(asset.getId()), List.of(green.getId()))
          .andExpect(status().is2xxSuccessful());
      assertArrayEquals(new String[] {green.getId()}, storedMarkings());
    }

    @Test
    @DisplayName(
        "given an asset marked above the caller's clearance, should silently exclude it from the"
            + " scope")
    void given_assetAboveClearance_should_excludeFromScope() throws Exception {
      // -- ARRANGE --
      // Seeded out of band: an ORM write of RED would itself be blocked, which is the guard
      // working but useless for arranging the fixture.
      entityManager.flush();
      jdbc.update(
          "UPDATE assets SET marking_ids = ? WHERE asset_id = ?",
          (Object) new String[] {red.getId()},
          asset.getId());
      entityManager.clear();

      // -- ACT / ASSERT --
      // The scope-resolution query is itself marking-filtered (is_marking_set_allowed), so this
      // asset is invisible to it - a no-op, not an error: 2xx with nothing touched.
      bulkReplaceMarkings(List.of(asset.getId()), List.of(green.getId()))
          .andExpect(status().is2xxSuccessful());
      assertArrayEquals(new String[] {red.getId()}, storedMarkings());
    }

    @Test
    @DisplayName("given neither explicit ids nor a search input, should return a bad request")
    void given_noSelection_should_returnBadRequest() throws Exception {
      // -- ACT / ASSERT --
      mvc.perform(
              put(ASSET_URI + "/markings/bulk")
                  .content(
                      asJsonString(
                          new AssetBulkReplaceMarkingsInput(
                              null, null, null, List.of(green.getId()))))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isBadRequest());
    }
  }

  // -- HELPERS --

  private ResultActions assignMarkings(List<String> markingIds) throws Exception {
    return mvc.perform(
        put(ASSET_URI + "/" + asset.getId() + "/markings")
            .content(asJsonString(new AssetUpdateMarkingsInput(markingIds)))
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .with(csrf()));
  }

  private ResultActions bulkReplaceMarkings(List<String> assetIds, List<String> markingIds)
      throws Exception {
    return mvc.perform(
        put(ASSET_URI + "/markings/bulk")
            .content(
                asJsonString(new AssetBulkReplaceMarkingsInput(null, assetIds, null, markingIds)))
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .with(csrf()));
  }

  /** Ground truth: what actually reached the column, bypassing Hibernate's first-level cache. */
  private String[] storedMarkings() {
    entityManager.flush();
    java.sql.Array array =
        jdbc.queryForObject(
            "SELECT marking_ids FROM assets WHERE asset_id = ?",
            java.sql.Array.class,
            asset.getId());
    if (array == null) {
      return new String[0];
    }
    try {
      String[] stored = (String[]) array.getArray();
      assertNotNull(stored);
      return stored;
    } catch (java.sql.SQLException e) {
      throw new IllegalStateException("could not read marking_ids", e);
    }
  }

  private MarkingDefinition marking(int order) {
    return markingDefinitionComposer
        .forMarkingDefinition(
            createMarkingDefinition(
                MarkingDefinition.TYPE_TLP, uniqueDefinition(), order, "#c62828"))
        .withTenantId(tenantId)
        .persist()
        .get();
  }
}
