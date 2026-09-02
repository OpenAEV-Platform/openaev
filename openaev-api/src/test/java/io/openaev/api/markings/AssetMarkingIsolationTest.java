package io.openaev.api.markings;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.MarkingDefinitionFixture.createMarkingDefinition;
import static io.openaev.utils.fixtures.MarkingDefinitionFixture.uniqueName;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.config.cache.MarkingClearanceCacheManager;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Group;
import io.openaev.database.model.MarkingDefinition;
import io.openaev.database.model.User;
import io.openaev.database.repository.GroupRepository;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.PaginationFixture;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Step 3.2 — the PoC's central claim, on a real table through real endpoints: a user cleared {@code
 * TLP:GREEN} cannot see a {@code TLP:RED} endpoint, <b>with no change to {@code EndpointRepository}
 * or {@code EndpointService} read code</b>.
 *
 * <p>Read that last clause as the assertion it is. Nothing in this test calls a marking-aware API.
 * It calls {@code POST /api/endpoints/search} and {@code GET /api/endpoints/{id}} exactly as any
 * other test does; the filtering happens because {@code assets} is on {@code
 * openaev.marking.active-tables} and the statement inspector rewrites the SQL underneath.
 *
 * <p>{@code @TestPropertySource} activates the table for this class only, so the rest of the suite
 * keeps running unmarked and this test stays honest about what activation costs.
 *
 * <p><b>Seeding is raw JDBC on purpose.</b> The marking dimension uses the same predicate for reads
 * and writes, so an ORM update that puts {@code TLP:RED} on a row would be blocked for a caller who
 * does not hold {@code TLP:RED} — the guard doing its job, but useless for arranging a fixture.
 * {@code JdbcTemplate} does not pass through Hibernate's statement inspector, which is precisely
 * why it is the right tool for out-of-band seeding and the wrong one for product code.
 */
@Transactional
@TestPropertySource(properties = "openaev.marking.active-tables=assets")
@WithMockUser(withCapabilities = {Capability.ACCESS_ASSETS})
@DisplayName("assets marking isolation through the real HTTP endpoints")
class AssetMarkingIsolationTest extends IntegrationTest {

  private static final String ENDPOINT_URI = "/api/endpoints";
  private static final String ENDPOINT_SEARCH_URI = ENDPOINT_URI + "/search";

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
  private Group group;

  private MarkingDefinition tlpGreen;
  private MarkingDefinition tlpRed;
  private MarkingDefinition papRed;

  private Endpoint unmarked;
  private Endpoint greenOnly;
  private Endpoint redOnly;
  private Endpoint greenAndPapRed;

  @BeforeEach
  void seed() {
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
    tlpGreen = marking(MarkingDefinition.TYPE_TLP, 60);
    tlpRed = marking(MarkingDefinition.TYPE_TLP, 70);
    papRed = marking(MarkingDefinition.TYPE_PAP, 70);

    unmarked = endpoint("unmarked");
    greenOnly = endpoint("green");
    redOnly = endpoint("red");
    greenAndPapRed = endpoint("green-and-pap-red");

    group =
        tenantGroupComposer
            .forGroup(TenantGroupFixture.getGroup("marking-iso-" + uniqueName()))
            .persist()
            .get();
    group.setUsers(new ArrayList<>(List.of(user)));
    groupRepository.save(group);

    // Flush before the raw-JDBC seeding below: the rows must exist in the database, not just in the
    // persistence context, for an UPDATE to find them.
    entityManager.flush();

    mark(greenOnly, tlpGreen);
    mark(redOnly, tlpRed);
    mark(greenAndPapRed, tlpGreen, papRed);
  }

  @Nested
  @DisplayName("with a TLP:GREEN clearance")
  class GreenCleared {

    @BeforeEach
    void grantGreen() {
      grant(tlpGreen);
    }

    @Test
    @DisplayName("given a search, should return unmarked and GREEN but never RED")
    void given_search_should_hideRed() throws Exception {
      // -- ACT --
      List<String> visible = searchEndpointIds();

      // -- ASSERT --
      // The unmarked row is the one people get wrong: fail-closed for marking means "see less", not
      // "see nothing". An empty marking set is contained in every clearance, so it stays visible.
      assertTrue(visible.contains(unmarked.getId()), "unmarked endpoint must stay visible");
      assertTrue(visible.contains(greenOnly.getId()), "GREEN endpoint must be visible");
      assertFalse(visible.contains(redOnly.getId()), "RED endpoint must be hidden");
    }

    @Test
    @DisplayName("given a direct GET on a RED endpoint, should 404 — not 403")
    void given_directGetOnRed_should_notFound() throws Exception {
      // -- ACT / ASSERT --
      // 404 rather than 403 is the whole point of filtering by rewrite: the row is not "refused",
      // it does not exist as far as this transaction's SQL is concerned. A 403 would confirm the
      // endpoint exists, which is itself a disclosure.
      mvc.perform(get(ENDPOINT_URI + "/" + redOnly.getId())).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("given a direct GET on a GREEN endpoint, should succeed")
    void given_directGetOnGreen_should_succeed() throws Exception {
      // -- ACT / ASSERT --
      // The negative case above means nothing without this one: it rules out "everything 404s".
      mvc.perform(get(ENDPOINT_URI + "/" + greenOnly.getId()))
          .andExpect(status().is2xxSuccessful());
    }

    @Test
    @DisplayName("given a row marked TLP:GREEN + PAP:RED, should hide it — AND, not OR")
    void given_multiMarkedRow_should_requireEveryMarking() throws Exception {
      // -- ACT --
      List<String> visible = searchEndpointIds();

      // -- ASSERT --
      // 🔴 The single most consequential semantic in the design. Holding ONE of a row's markings is
      // not enough — a row is visible only when the reader holds them ALL. If this ever flips to
      // OR, every multi-marked row leaks to anyone holding its weakest label.
      assertFalse(
          visible.contains(greenAndPapRed.getId()),
          "TLP:GREEN + PAP:RED must be hidden from a TLP:GREEN-only clearance");
      assertTrue(
          visible.contains(greenOnly.getId()), "the TLP:GREEN-only row must still be visible");
    }
  }

  @Nested
  @DisplayName("clearance boundaries")
  class Boundaries {

    @Test
    @DisplayName("given no clearance at all, should still see unmarked rows and nothing marked")
    void given_noClearance_should_seeOnlyUnmarked() throws Exception {
      // -- ACT --
      List<String> visible = searchEndpointIds();

      // -- ASSERT --
      // The default state of every user before anyone assigns anything. Activation must not be a
      // platform-wide blackout, or nobody would ever be able to turn it on.
      assertTrue(visible.contains(unmarked.getId()), "unmarked endpoint must stay visible");
      assertFalse(visible.contains(greenOnly.getId()));
      assertFalse(visible.contains(redOnly.getId()));
    }

    @Test
    @DisplayName("given a TLP:RED clearance, should also see GREEN — higher implies lower")
    void given_redClearance_should_alsoSeeGreen() throws Exception {
      // -- ARRANGE --
      grant(tlpRed);

      // -- ACT --
      List<String> visible = searchEndpointIds();

      // -- ASSERT --
      // Ordinality is expanded in Java before the GUC is written, so the SQL predicate stays a flat
      // containment test. This asserts that expansion actually reaches the database.
      assertTrue(visible.contains(redOnly.getId()), "RED endpoint must be visible");
      assertTrue(visible.contains(greenOnly.getId()), "RED clearance must imply GREEN");
      assertTrue(visible.contains(unmarked.getId()));
    }

    @Test
    @DisplayName("given both scales granted, should see the row that needs both")
    void given_bothScales_should_seeTheMultiMarkedRow() throws Exception {
      // -- ARRANGE --
      // Types are independent scales: TLP says nothing about PAP, so both must be granted.
      grant(tlpGreen, papRed);

      // -- ACT --
      List<String> visible = searchEndpointIds();

      // -- ASSERT --
      assertTrue(
          visible.contains(greenAndPapRed.getId()),
          "holding both TLP:GREEN and PAP:RED must reveal the row needing both");
      assertFalse(visible.contains(redOnly.getId()), "TLP:RED is still not held");
    }
  }

  // -- HELPERS --

  private List<String> searchEndpointIds() throws Exception {
    String response =
        mvc.perform(
                post(ENDPOINT_SEARCH_URI)
                    .content(asJsonString(PaginationFixture.getDefault().size(200).build()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(response, "$.content[*].asset_id");
  }

  /** Grants markings to the group the user belongs to, and drops the cached clearance. */
  private void grant(MarkingDefinition... markings) {
    for (MarkingDefinition marking : markings) {
      jdbc.update(
          "INSERT INTO groups_markings (group_id, marking_id) VALUES (?, ?)"
              + " ON CONFLICT DO NOTHING",
          group.getId(),
          marking.getId());
    }
    clearanceCache.evictForUser(userId);
  }

  /** Out-of-band write: see the class javadoc for why this is not an ORM save. */
  private void mark(Endpoint endpoint, MarkingDefinition... markings) {
    jdbc.update(
        "UPDATE assets SET marking_ids = ? WHERE asset_id = ?",
        (Object)
            java.util.Arrays.stream(markings).map(MarkingDefinition::getId).toArray(String[]::new),
        endpoint.getId());
  }

  private MarkingDefinition marking(String type, int order) {
    return markingDefinitionComposer
        .forMarkingDefinition(createMarkingDefinition(type, uniqueName(), order, "#c62828"))
        .withTenantId(tenantId)
        .persist()
        .get();
  }

  private Endpoint endpoint(String name) {
    return endpointComposer
        .forEndpoint(EndpointFixture.createEndpoint("marking-iso-" + name + "-" + uniqueName()))
        .persist()
        .get();
  }
}
