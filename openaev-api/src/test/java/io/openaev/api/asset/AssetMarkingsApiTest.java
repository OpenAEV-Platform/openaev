package io.openaev.api.asset;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.JsonTestUtils.asStringJson;
import static io.openaev.utils.fixtures.MarkingDefinitionFixture.createMarkingDefinition;
import static io.openaev.utils.fixtures.MarkingDefinitionFixture.uniqueDefinition;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.api.asset.dto.AssetBulkReplaceMarkingsInput;
import io.openaev.config.cache.MarkingClearanceCacheManager;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Group;
import io.openaev.database.model.MarkingDefinition;
import io.openaev.database.model.User;
import io.openaev.database.repository.GroupRepository;
import io.openaev.rest.asset.ai_targets.form.AiTargetInput;
import io.openaev.rest.asset.endpoint.form.EndpointInput;
import io.openaev.utils.fixtures.AiTargetFixture;
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
import java.util.Map;
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
 * Asset markings, written with the asset itself or in bulk — design step 3.3, the write paths that
 * put a label on a row.
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
@WithMockUser(withCapabilities = {Capability.MANAGE_ASSETS, Capability.MANAGE_MARKING_ASSIGNMENT})
@DisplayName("Asset markings API")
class AssetMarkingsApiTest extends IntegrationTest {

  private static final String ASSET_URI = "/api/assets";
  private static final String TENANT_ASSET_URI = "/api/tenants/{tenantId}/assets";
  private static final String ENDPOINT_CREATE_URI = "/api/tenants/{tenantId}/endpoints/agentless";
  private static final String AI_TARGET_CREATE_URI = "/api/tenants/{tenantId}/ai_targets";
  private static final String AI_TARGET_UPDATE_URI =
      "/api/tenants/{tenantId}/ai_targets/{aiTargetId}";
  private static final String ENDPOINT_UPDATE_URI =
      "/api/tenants/{tenantId}/endpoints/{endpointId}";

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

    entityManager.flush();

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
    @DisplayName("given a marking the caller does not hold, should refuse with 403")
    void given_unheldMarking_should_forbid() throws Exception {
      // -- ACT / ASSERT --
      assignMarkings(List.of(red.getId())).andExpect(status().isForbidden());

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
      assertEquals(0, storedMarkings().length);
    }

    @Test
    @DisplayName("given an asset marked above the caller's clearance, should not find it")
    void given_assetAboveClearance_should_notFound() throws Exception {
      // -- ARRANGE --
      entityManager.flush();
      jdbc.update(
          "UPDATE assets SET marking_ids = ? WHERE asset_id = ?",
          (Object) new String[] {red.getId()},
          asset.getId());
      entityManager.clear();

      // -- ACT / ASSERT --
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
        "given the caller lacks MANAGE_MARKING_ASSIGNMENT, should refuse an addition with 403")
    @WithMockUser(withCapabilities = {Capability.MANAGE_ASSETS})
    void given_noMarkingCapability_withAddition_should_forbid() throws Exception {
      // -- ACT / ASSERT --
      bulkReplaceMarkings(List.of(asset.getId()), List.of(green.getId()))
          .andExpect(status().isForbidden());
      assertEquals(0, storedMarkings().length);
    }

    @Test
    @DisplayName(
        "given the caller lacks MANAGE_MARKING_ASSIGNMENT, should refuse a removal with 403")
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
      // GREEN dropped, nothing added - a pure removal, still gated by MANAGE_MARKING_ASSIGNMENT.
      bulkReplaceMarkings(List.of(asset.getId()), List.of()).andExpect(status().isForbidden());
      assertArrayEquals(new String[] {green.getId()}, storedMarkings());
    }

    @Test
    @DisplayName(
        "given an asset marked above the caller's clearance, should silently exclude it from the"
            + " scope")
    void given_assetAboveClearance_should_excludeFromScope() throws Exception {
      // -- ARRANGE --
      entityManager.flush();
      jdbc.update(
          "UPDATE assets SET marking_ids = ? WHERE asset_id = ?",
          (Object) new String[] {red.getId()},
          asset.getId());
      entityManager.clear();

      // -- ACT / ASSERT --
      bulkReplaceMarkings(List.of(asset.getId()), List.of(green.getId()))
          .andExpect(status().is2xxSuccessful());
      assertArrayEquals(new String[] {red.getId()}, storedMarkings());
    }

    @Test
    @DisplayName("given neither explicit ids nor a search input, should return a bad request")
    void given_noSelection_should_returnBadRequest() throws Exception {
      // -- PREPARE --
      AssetBulkReplaceMarkingsInput input = new AssetBulkReplaceMarkingsInput();
      input.setMarkingIds(List.of(green.getId()));

      // -- ACT / ASSERT --
      mvc.perform(
              put(TENANT_ASSET_URI + "/markings/bulk", tenantId)
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isBadRequest());
    }
  }

  @Nested
  @DisplayName("creating an asset with markings")
  class Creating {

    @Test
    @DisplayName("given a marking the caller holds, should create the endpoint already marked")
    void given_heldMarking_should_createMarkedEndpoint() throws Exception {
      // -- ACT --
      String body =
          createAsset(ENDPOINT_CREATE_URI, endpointInput(), List.of(green.getId()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- ASSERT --
      assertArrayEquals(
          new String[] {green.getId()}, storedMarkings(JsonPath.read(body, "$.asset_id")));
    }

    @Test
    @DisplayName("given a marking the caller holds, should create the AI target already marked")
    void given_heldMarking_should_createMarkedAiTarget() throws Exception {
      // -- ACT --
      String body =
          createAsset(
                  AI_TARGET_CREATE_URI,
                  AiTargetFixture.createDefaultAiTargetInput("asset-markings"),
                  List.of(green.getId()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- ASSERT --
      assertArrayEquals(
          new String[] {green.getId()}, storedMarkings(JsonPath.read(body, "$.asset_id")));
    }

    @Test
    @DisplayName("given a marking the caller does not hold, should refuse and create nothing")
    void given_unheldMarking_should_forbidAndCreateNothing() throws Exception {
      // -- ARRANGE --
      EndpointInput input = endpointInput();

      // -- ACT --
      createAsset(ENDPOINT_CREATE_URI, input, List.of(red.getId()))
          .andExpect(status().isForbidden());

      // -- ASSERT --
      assertEquals(0, assetsNamed(input.getName()));
    }
  }

  @Nested
  @DisplayName("updating an asset with markings")
  class Updating {

    @Test
    @DisplayName("given changed markings, should update the endpoint and its markings together")
    void given_changedMarkings_should_updateEndpointAndMarkings() throws Exception {
      // -- ARRANGE --
      EndpointInput input = endpointInput();

      // -- ACT --
      updateAsset(ENDPOINT_UPDATE_URI, asset.getId(), input, List.of(green.getId()))
          .andExpect(status().is2xxSuccessful());

      // -- ASSERT --
      assertEquals(1, assetsNamed(input.getName()));
      assertArrayEquals(new String[] {green.getId()}, storedMarkings());
    }

    @Test
    @DisplayName("given changed markings, should update the AI target and its markings together")
    void given_changedMarkings_should_updateAiTargetAndMarkings() throws Exception {
      // -- ARRANGE --
      AiTargetInput input = AiTargetFixture.createDefaultAiTargetInput("asset-markings");
      String aiTargetId =
          JsonPath.read(
              createAsset(AI_TARGET_CREATE_URI, input, List.of())
                  .andExpect(status().is2xxSuccessful())
                  .andReturn()
                  .getResponse()
                  .getContentAsString(),
              "$.asset_id");

      // -- ACT --
      updateAsset(AI_TARGET_UPDATE_URI, aiTargetId, input, List.of(green.getId()))
          .andExpect(status().is2xxSuccessful());

      // -- ASSERT --
      assertArrayEquals(new String[] {green.getId()}, storedMarkings(aiTargetId));
    }

    @Test
    @DisplayName(
        "given unchanged markings and no MANAGE_MARKING_ASSIGNMENT, should update the endpoint")
    @WithMockUser(withCapabilities = {Capability.MANAGE_ASSETS})
    void given_unchangedMarkingsWithoutCapability_should_updateEndpoint() throws Exception {
      // -- ARRANGE --
      storeMarkings(green.getId());
      EndpointInput input = endpointInput();

      // -- ACT --
      updateAsset(ENDPOINT_UPDATE_URI, asset.getId(), input, List.of(green.getId()))
          .andExpect(status().is2xxSuccessful());

      // -- ASSERT --
      assertEquals(1, assetsNamed(input.getName()));
      assertArrayEquals(new String[] {green.getId()}, storedMarkings());
    }

    @Test
    @DisplayName("given no markings in the request, should keep the endpoint's markings")
    void given_noMarkings_should_keepMarkings() throws Exception {
      // -- ARRANGE --
      storeMarkings(green.getId());

      // -- ACT --
      updateAsset(ENDPOINT_UPDATE_URI, asset.getId(), endpointInput(), null)
          .andExpect(status().is2xxSuccessful());

      // -- ASSERT --
      assertArrayEquals(new String[] {green.getId()}, storedMarkings());
    }
  }

  // -- HELPERS --

  private ResultActions assignMarkings(List<String> markingIds) throws Exception {
    return updateAsset(ENDPOINT_UPDATE_URI, asset.getId(), endpointInput(), markingIds);
  }

  private ResultActions bulkReplaceMarkings(List<String> assetIds, List<String> markingIds)
      throws Exception {
    AssetBulkReplaceMarkingsInput input = new AssetBulkReplaceMarkingsInput();
    input.setAssetIdsToProcess(assetIds);
    input.setMarkingIds(markingIds);
    return mvc.perform(
        put(TENANT_ASSET_URI + "/markings/bulk", tenantId)
            .content(asJsonString(input))
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .with(csrf()));
  }

  private ResultActions createAsset(String uri, Object input, List<String> markingIds)
      throws Exception {
    return mvc.perform(
        post(uri, tenantId)
            .content(withMarkings(input, markingIds))
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .with(csrf()));
  }

  private ResultActions updateAsset(
      String uri, String assetId, Object input, List<String> markingIds) throws Exception {
    return mvc.perform(
        put(uri, tenantId, assetId)
            .content(withMarkings(input, markingIds))
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .with(csrf()));
  }

  private static String withMarkings(Object input, List<String> markingIds) {
    Map<String, Object> body = asStringJson(asJsonString(input), Map.class);
    if (markingIds != null) {
      body.put("asset_markings", markingIds);
    }
    return asJsonString(body);
  }

  private static EndpointInput endpointInput() {
    EndpointInput input = EndpointFixture.createWindowsEndpointInput(List.of());
    input.setName("asset-markings-" + uniqueDefinition());
    return input;
  }

  private void storeMarkings(String... markingIds) {
    entityManager.flush();
    jdbc.update(
        "UPDATE assets SET marking_ids = ? WHERE asset_id = ?", (Object) markingIds, asset.getId());
    entityManager.clear();
  }

  private int assetsNamed(String name) {
    entityManager.flush();
    return jdbc.queryForObject(
        "SELECT count(*) FROM assets WHERE asset_name = ?", Integer.class, name);
  }

  /** Ground truth: what actually reached the column, bypassing Hibernate's first-level cache. */
  private String[] storedMarkings() {
    return storedMarkings(asset.getId());
  }

  private String[] storedMarkings(String assetId) {
    entityManager.flush();
    java.sql.Array array =
        jdbc.queryForObject(
            "SELECT marking_ids FROM assets WHERE asset_id = ?", java.sql.Array.class, assetId);
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
