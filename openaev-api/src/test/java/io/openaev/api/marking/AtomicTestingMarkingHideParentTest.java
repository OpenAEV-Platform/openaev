package io.openaev.api.marking;

import static io.openaev.rest.atomic_testing.AtomicTestingApi.ATOMIC_TESTING_URI;
import static io.openaev.rest.finding.FindingApi.FINDING_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.MarkingDefinitionFixture.createMarkingDefinition;
import static io.openaev.utils.fixtures.MarkingDefinitionFixture.uniqueDefinition;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.config.cache.MarkingClearanceCacheManager;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Finding;
import io.openaev.database.model.Group;
import io.openaev.database.model.Inject;
import io.openaev.database.model.MarkingDefinition;
import io.openaev.database.model.User;
import io.openaev.database.repository.FindingRepository;
import io.openaev.database.repository.GroupRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.utils.fixtures.AgentFixture;
import io.openaev.utils.fixtures.AssetGroupFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.ExecutorFixture;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.FindingFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectStatusFixture;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.fixtures.TenantGroupFixture;
import io.openaev.utils.fixtures.composers.AgentComposer;
import io.openaev.utils.fixtures.composers.AssetGroupComposer;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.FindingComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectStatusComposer;
import io.openaev.utils.fixtures.composers.MarkingDefinitionComposer;
import io.openaev.utils.fixtures.composers.TenantGroupComposer;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.Arrays;
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
 * Task 4, Option 2 "hide parents" (variant C-2), through the real HTTP endpoints: an Atomic Testing
 * that targets an asset outside the caller's clearance does not exist for that caller, and neither
 * do the findings of its runs.
 *
 * <p>Nothing here calls a marking-aware API. The filtering happens because {@code injects} and
 * {@code findings} are on {@code openaev.marking.derived-tables} and the statement inspector adds
 * the {@code can_see_*} predicates underneath. {@code assets} stays on {@code active-tables}
 * because {@code @TestPropertySource} replaces the property rather than adding to it.
 *
 * <p><b>Seeding is raw JDBC on purpose</b>, as in {@code AssetMarkingIsolationTest}: marking an
 * asset through the ORM would itself be filtered by the marking dimension. The persistence context
 * is cleared after seeding so a lookup by id has to go through SQL, and therefore through the
 * inspector, instead of being served from the first-level cache.
 */
@Transactional
@TestPropertySource(
    properties = {
      "openaev.marking.active-tables=assets",
      "openaev.marking.derived-tables=injects,findings"
    })
@WithMockUser(
    withCapabilities = {
      Capability.ACCESS_ASSESSMENT,
      Capability.LAUNCH_ASSESSMENT,
      Capability.ACCESS_FINDINGS,
      Capability.ACCESS_ASSETS
    })
@DisplayName("Atomic Testing and its findings are hidden when they hold a restricted asset")
class AtomicTestingMarkingHideParentTest extends IntegrationTest {

  private static final String ATOMIC_TESTING_SEARCH_URI = ATOMIC_TESTING_URI + "/search";
  private static final String FINDING_SEARCH_URI = FINDING_URI + "/search";

  @Autowired private MockMvc mvc;
  @Autowired private DataSource dataSource;
  @Autowired private GroupRepository groupRepository;
  @Autowired private InjectRepository injectRepository;
  @Autowired private FindingRepository findingRepository;
  @Autowired private TenantScopedTransaction tenantScopedTransaction;
  @Autowired private TenantGroupComposer tenantGroupComposer;
  @Autowired private MarkingDefinitionComposer markingDefinitionComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private AgentComposer agentComposer;
  @Autowired private AssetGroupComposer assetGroupComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectStatusComposer injectStatusComposer;
  @Autowired private FindingComposer findingComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private ExecutorFixture executorFixture;
  @Autowired private MarkingClearanceCacheManager clearanceCache;
  @PersistenceContext private EntityManager entityManager;

  private JdbcTemplate jdbc;
  private String tenantId;
  private String userId;
  private Group group;

  private MarkingDefinition tlpGreen;
  private MarkingDefinition tlpRed;

  private Endpoint assetGreen;
  private Endpoint assetRed;
  private Endpoint assetPlain;

  /** Targets ASSET_GREEN and ASSET_RED (US1): must disappear for a TLP:GREEN user. */
  private Inject atMixed;

  /** Targets ASSET_GREEN and an unmarked asset (US2): visible and launchable for TLP:GREEN. */
  private Inject atGreen;

  private Finding atMixedFindingOnGreen;
  private Finding atMixedFindingOnRed;
  private Finding atGreenFinding;

  /** Reaches ASSET_RED only through a static asset group. */
  private Inject atThroughGroup;

  /** A finding on ASSET_RED produced by a simulation inject (not an Atomic Testing). */
  private Finding simulationFindingOnRed;

  @BeforeEach
  void seed() {
    jdbc = new JdbcTemplate(dataSource);
    tenantGroupComposer.reset();
    markingDefinitionComposer.reset();
    endpointComposer.reset();
    agentComposer.reset();
    assetGroupComposer.reset();
    injectComposer.reset();
    injectStatusComposer.reset();
    findingComposer.reset();
    exerciseComposer.reset();

    tenantId = TenantContext.getCurrentTenant();
    User user = testUserHolder.get();
    userId = user.getId();
    tenantRepository.addUserToTenant(userId, tenantId);
    tenantMembershipCacheManager.evict(userId, tenantId);

    // Orders above the seeded 10..50 band so a fixture never ties with a default.
    tlpGreen = marking(MarkingDefinition.TYPE_TLP, 60);
    tlpRed = marking(MarkingDefinition.TYPE_TLP, 70);

    EndpointComposer.Composer green = endpointWithAgent("green");
    EndpointComposer.Composer red = endpointWithAgent("red");
    EndpointComposer.Composer plain = endpointWithAgent("plain");

    FindingComposer.Composer mixedOnGreen = finding().withEndpoint(green);
    FindingComposer.Composer mixedOnRed = finding().withEndpoint(red);
    FindingComposer.Composer greenFinding = finding().withEndpoint(green);

    atMixed =
        atomicTesting()
            .withEndpoint(green)
            .withEndpoint(red)
            .withFinding(mixedOnGreen)
            .withFinding(mixedOnRed)
            .persist()
            .get();
    atGreen =
        atomicTesting()
            .withEndpoint(green)
            .withEndpoint(plain)
            .withFinding(greenFinding)
            .persist()
            .get();

    atThroughGroup =
        atomicTesting()
            .withAssetGroup(
                assetGroupComposer
                    .forAssetGroup(
                        AssetGroupFixture.createDefaultAssetGroup(
                            "hide-parent-group-" + uniqueDefinition()))
                    .withAsset(red))
            .persist()
            .get();

    FindingComposer.Composer onRed = finding().withEndpoint(red);
    exerciseComposer
        .forExercise(ExerciseFixture.createDefaultExercise())
        .withInject(
            injectComposer
                .forInject(InjectFixture.getDefaultInject())
                .withEndpoint(red)
                .withFinding(onRed))
        .persist();
    simulationFindingOnRed = onRed.get();

    assetGreen = green.get();
    assetRed = red.get();
    assetPlain = plain.get();
    atMixedFindingOnGreen = mixedOnGreen.get();
    atMixedFindingOnRed = mixedOnRed.get();
    atGreenFinding = greenFinding.get();

    group =
        tenantGroupComposer
            .forGroup(TenantGroupFixture.getGroup("hide-parent-" + uniqueDefinition()))
            .persist()
            .get();
    group.setUsers(new ArrayList<>(List.of(user)));
    groupRepository.save(group);

    // Flush before the raw-JDBC marking below: the rows must exist in the database for the UPDATE
    // to find them.
    entityManager.flush();

    mark(assetGreen, tlpGreen);
    mark(assetRed, tlpRed);

    // Drop the first-level cache: every read below must go through SQL, hence the inspector.
    entityManager.clear();
  }

  @Nested
  @DisplayName("US1 — a TLP:GREEN user and an Atomic Testing targeting a TLP:RED asset")
  class US1HiddenAtomicTesting {

    @BeforeEach
    void grantGreen() {
      grant(tlpGreen);
    }

    @Test
    @DisplayName("given the Atomic Testing list, should not contain it")
    void given_atomicTestingSearch_should_notListMixedAtomicTesting() throws Exception {
      // -- ACT --
      List<String> visible = searchAtomicTestingIds();

      // -- ASSERT --
      assertThat(visible).doesNotContain(atMixed.getId());
      // Rules out "everything is hidden": the green-only one is still there.
      assertThat(visible).contains(atGreen.getId());
    }

    @Test
    @DisplayName("given a direct GET, should 404 exactly like an id that does not exist")
    void given_directGet_should_notFound() throws Exception {
      // -- ACT --
      int hidden =
          mvc.perform(get(ATOMIC_TESTING_URI + "/" + atMixed.getId()))
              .andReturn()
              .getResponse()
              .getStatus();
      int missing =
          mvc.perform(get(ATOMIC_TESTING_URI + "/" + uniqueDefinition()))
              .andReturn()
              .getResponse()
              .getStatus();

      // -- ASSERT --
      // Same status as a missing id: a 403 would confirm the Atomic Testing exists.
      assertThat(hidden).isEqualTo(404).isEqualTo(missing);
    }

    @Test
    @DisplayName("given a launch, should fail exactly like an id that does not exist")
    void given_launch_should_failLikeMissing() throws Exception {
      // -- ACT --
      int hidden =
          mvc.perform(post(ATOMIC_TESTING_URI + "/" + atMixed.getId() + "/launch").with(csrf()))
              .andReturn()
              .getResponse()
              .getStatus();
      int missing =
          mvc.perform(post(ATOMIC_TESTING_URI + "/" + uniqueDefinition() + "/launch").with(csrf()))
              .andReturn()
              .getResponse()
              .getStatus();

      // -- ASSERT --
      assertThat(hidden).isGreaterThanOrEqualTo(400).isEqualTo(missing);
    }

    @Test
    @DisplayName("given the Findings page, should not show any finding of the hidden run")
    void given_findingSearch_should_hideEveryFindingOfTheRun() throws Exception {
      // -- ACT --
      List<String> values = findingValues(FINDING_SEARCH_URI);

      // -- ASSERT --
      // The finding on ASSET_GREEN is hidden too: it belongs to a run the user cannot see.
      assertThat(values)
          .doesNotContain(atMixedFindingOnGreen.getValue(), atMixedFindingOnRed.getValue());
      assertThat(values).contains(atGreenFinding.getValue());
    }

    @Test
    @DisplayName("given the findings of the green endpoint, should not show the hidden run's one")
    void given_findingsByEndpoint_should_hideTheHiddenRunsFinding() throws Exception {
      // -- ACT --
      List<String> values =
          findingValues(FINDING_URI + "/endpoints/" + assetGreen.getId() + "/search");

      // -- ASSERT --
      assertThat(values).doesNotContain(atMixedFindingOnGreen.getValue());
      assertThat(values).contains(atGreenFinding.getValue());
    }

    @Test
    @DisplayName("given the findings of the hidden Atomic Testing, should return none of them")
    void given_findingsByInject_should_returnNothing() throws Exception {
      // -- ACT --
      List<String> values = findingValues(FINDING_URI + "/injects/" + atMixed.getId() + "/search");

      // -- ASSERT --
      assertThat(values).isEmpty();
    }
  }

  @Nested
  @DisplayName("US2 — a TLP:GREEN user and an Atomic Testing targeting only visible assets")
  class US2VisibleAtomicTesting {

    @BeforeEach
    void grantGreen() {
      grant(tlpGreen);
    }

    @Test
    @DisplayName("given a direct GET, should return it")
    void given_directGet_should_succeed() throws Exception {
      // -- ACT / ASSERT --
      mvc.perform(get(ATOMIC_TESTING_URI + "/" + atGreen.getId()))
          .andExpect(status().is2xxSuccessful());
    }

    @Test
    @DisplayName("given a launch, should queue it")
    void given_launch_should_queue() throws Exception {
      // -- ACT / ASSERT --
      mvc.perform(post(ATOMIC_TESTING_URI + "/" + atGreen.getId() + "/launch").with(csrf()))
          .andExpect(status().is2xxSuccessful());
    }

    @Test
    @DisplayName("given its findings, should return them")
    void given_findingsByInject_should_returnThem() throws Exception {
      // -- ACT --
      List<String> values = findingValues(FINDING_URI + "/injects/" + atGreen.getId() + "/search");

      // -- ASSERT --
      assertThat(values).contains(atGreenFinding.getValue());
    }
  }

  @Nested
  @DisplayName("an admin")
  class Admin {

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("given an admin, should see the mixed Atomic Testing and all of its findings")
    void given_admin_should_seeEverything() throws Exception {
      // -- ACT --
      List<String> atomicTestings = searchAtomicTestingIds();
      List<String> findings =
          findingValues(FINDING_URI + "/injects/" + atMixed.getId() + "/search");

      // -- ASSERT --
      assertThat(atomicTestings).contains(atMixed.getId(), atGreen.getId());
      assertThat(findings)
          .contains(atMixedFindingOnGreen.getValue(), atMixedFindingOnRed.getValue());
      mvc.perform(get(ATOMIC_TESTING_URI + "/" + atMixed.getId()))
          .andExpect(status().is2xxSuccessful());
    }
  }

  @Nested
  @DisplayName("clearance boundaries")
  class Boundaries {

    @Test
    @DisplayName("given no clearance, should see only Atomic Testing with no marked target")
    void given_noClearance_should_hideAnyMarkedTarget() throws Exception {
      // -- ARRANGE --
      Inject atPlainOnly = atomicTesting().withEndpoint(existing(assetPlain)).persist().get();
      entityManager.flush();
      entityManager.clear();

      // -- ACT --
      List<String> visible = searchAtomicTestingIds();

      // -- ASSERT --
      assertThat(visible).contains(atPlainOnly.getId());
      assertThat(visible).doesNotContain(atMixed.getId(), atGreen.getId());
    }

    @Test
    @DisplayName("given a TLP:RED clearance, should see both — higher implies lower")
    void given_redClearance_should_seeBoth() throws Exception {
      // -- ARRANGE --
      grant(tlpRed);

      // -- ACT --
      List<String> visible = searchAtomicTestingIds();

      // -- ASSERT --
      assertThat(visible).contains(atMixed.getId(), atGreen.getId());
    }

    @Test
    @DisplayName("given a TLP:RED asset reached through a static asset group, should hide it")
    void given_restrictedAssetInStaticGroup_should_hide() throws Exception {
      // -- ARRANGE --
      grant(tlpGreen);

      // -- ACT --
      List<String> visible = searchAtomicTestingIds();

      // -- ASSERT --
      assertThat(visible).doesNotContain(atThroughGroup.getId());
      assertThat(visible).contains(atGreen.getId());
    }

    @Test
    @DisplayName("given a simulation finding on a TLP:RED asset, should hide the finding (D3)")
    void given_simulationFindingOnRedAsset_should_hideFinding() throws Exception {
      // -- ARRANGE --
      grant(tlpGreen);

      // -- ACT --
      List<String> values = findingValues(FINDING_SEARCH_URI);

      // -- ASSERT --
      assertThat(values).doesNotContain(simulationFindingOnRed.getValue());
      assertThat(values).contains(atGreenFinding.getValue());
    }
  }

  @Nested
  @DisplayName("background work")
  class Background {

    @Test
    @DisplayName("given system clearance, should still see the hidden Atomic Testing and findings")
    void given_systemClearance_should_seeHiddenRows() {
      // -- ARRANGE --
      // The execution path reads through TenantScopedTransaction, which writes the tenant's full
      // marking set into the scope. Reproduce that scope on this transaction, with no grant at all
      // for the user: what the job sees must not depend on who launched it.
      tenantScopedTransaction.setScopeOnCurrentTransaction(TxCtx.forTenant(tenantId));

      // -- ACT / ASSERT --
      assertThat(injectRepository.findById(atMixed.getId())).isPresent();
      assertThat(findingRepository.findById(atMixedFindingOnRed.getId())).isPresent();
    }
  }

  // -- HELPERS --

  private List<String> searchAtomicTestingIds() throws Exception {
    String response =
        mvc.perform(
                post(ATOMIC_TESTING_SEARCH_URI)
                    .content(asJsonString(PaginationFixture.getDefault().size(200).build()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(response, "$.content[*].inject_id");
  }

  /** Finding values returned by a findings search; an error status counts as "nothing visible". */
  private List<String> findingValues(String uri) throws Exception {
    var response =
        mvc.perform(
                post(uri)
                    .content(asJsonString(PaginationFixture.getDefault().size(200).build()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andReturn()
            .getResponse();
    if (response.getStatus() >= 400) {
      return List.of();
    }
    return JsonPath.read(response.getContentAsString(), "$.content[*].finding_value");
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
        (Object) Arrays.stream(markings).map(MarkingDefinition::getId).toArray(String[]::new),
        endpoint.getId());
  }

  private MarkingDefinition marking(String type, int order) {
    return markingDefinitionComposer
        .forMarkingDefinition(createMarkingDefinition(type, uniqueDefinition(), order, "#c62828"))
        .withTenantId(tenantId)
        .persist()
        .get();
  }

  private EndpointComposer.Composer endpointWithAgent(String name) {
    return endpointComposer
        .forEndpoint(
            EndpointFixture.createEndpoint("hide-parent-" + name + "-" + uniqueDefinition()))
        .withAgent(
            agentComposer.forAgent(
                AgentFixture.createDefaultAgentSession(executorFixture.getDefaultExecutor())));
  }

  /** Wraps an already persisted endpoint, re-attached, so a new inject can target it. */
  private EndpointComposer.Composer existing(Endpoint endpoint) {
    return endpointComposer.forEndpoint(entityManager.find(Endpoint.class, endpoint.getId()));
  }

  private InjectComposer.Composer atomicTesting() {
    return injectComposer
        .forInject(InjectFixture.getDefaultInject())
        .withInjectStatus(
            injectStatusComposer.forInjectStatus(InjectStatusFixture.createDraftInjectStatus()));
  }

  private FindingComposer.Composer finding() {
    return findingComposer.forFinding(FindingFixture.createDefaultTextFindingWithRandomValue());
  }
}
