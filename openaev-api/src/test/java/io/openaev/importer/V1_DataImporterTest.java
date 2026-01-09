package io.openaev.importer;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_METHOD;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.database.model.Tag;
import io.openaev.database.repository.*;
import io.openaev.service.scenario.ScenarioService;
import io.openaev.utils.constants.Constants;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.*;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_METHOD)
class V1_DataImporterTest extends IntegrationTest {

  @Autowired private V1_DataImporter importer;

  @Autowired private ExerciseRepository exerciseRepository;

  @Autowired private TeamRepository teamRepository;

  @Autowired private UserRepository userRepository;

  @Autowired private OrganizationRepository organizationRepository;

  @Autowired private TagRepository tagRepository;

  @Autowired private ScenarioRepository scenarioRepository;

  @Autowired private ScenarioService scenarioService;

  @Autowired private PayloadRepository payloadRepository;

  @Autowired private AttackPatternRepository attackPatternRepository;

  @Autowired private KillChainPhaseRepository killChainPhaseRepository;

  @Autowired private InjectorRepository injectorRepository;

  @Autowired private InjectorContractRepository injectorContractRepository;

  @Autowired private InjectRepository injectRepository;

  private JsonNode importNode;

  public static final String EXERCISE_NAME =
      "Test Exercise%s".formatted(Constants.IMPORTED_OBJECT_NAME_SUFFIX);
  public static final String TEAM_NAME = "Animation team";
  public static final String USER_EMAIL = "Romuald.Lemesle@openaev.io";
  public static final String ORGANIZATION_NAME = "Filigran";
  public static final String TAG_NAME = "crisis exercise";
  public static final String ATTACK_PATTERN_EXTERNAL_ID = "ATTACK_PATTERN_EXTERNAL_ID";
  public static final String KILLCHAIN_EXTERNAL_ID = "KILLCHAIN_EXTERNAL_ID";
  public static final String PAYLOAD_EXTERNAL_ID = "PAYLOAD_EXTERNAL_ID";
  public static final String NMAP_DUMMY_INJECTOR_TYPE = "openaev_nmap_dummy";

  @BeforeEach
  void cleanBefore() throws IOException {
    killChainPhaseRepository.deleteAll();
    attackPatternRepository.deleteAll();
    exerciseRepository.deleteAll();
    scenarioRepository.deleteAll();
    injectRepository.deleteAll();
    injectorContractRepository.deleteAll();
    injectorRepository.deleteAll();
    MockitoAnnotations.openMocks(this);
    ObjectMapper mapper = new ObjectMapper();
    String jsonContent =
        new String(
            Files.readAllBytes(Paths.get("src/test/resources/importer-v1/import-data.json")));
    this.importNode = mapper.readTree(jsonContent);
  }

  @Test
  @Transactional
  void testImportData() {
    // -- EXECUTE --
    this.importer.importData(
        this.importNode, Map.of(), null, null, null, null, Constants.IMPORTED_OBJECT_NAME_SUFFIX);

    // -- ASSERT --
    Optional<Exercise> exercise = this.exerciseRepository.findOne(exerciseByName(EXERCISE_NAME));
    assertTrue(exercise.isPresent());

    Optional<Team> team = this.teamRepository.findByName(TEAM_NAME);
    assertTrue(team.isPresent());
    assertEquals(1, team.get().getUsersNumber());
    assertEquals(ORGANIZATION_NAME, team.get().getOrganization().getName());
    assertEquals(1, team.get().getTags().size());

    Optional<User> user = this.userRepository.findByEmailIgnoreCase(USER_EMAIL);
    assertTrue(user.isPresent());
    assertEquals(ORGANIZATION_NAME, user.get().getOrganization().getName());
    assertEquals(1, user.get().getTags().size());

    List<Organization> organization =
        this.organizationRepository.findByNameIgnoreCase(ORGANIZATION_NAME);
    assertFalse(organization.isEmpty());
    assertEquals(ORGANIZATION_NAME, organization.getFirst().getName());

    List<Tag> tag = this.tagRepository.findByNameIgnoreCase(TAG_NAME);
    assertFalse(tag.isEmpty());
    assertEquals(TAG_NAME, tag.getFirst().getName());
  }

  @Test
  @Transactional
  void testScenario_with_attackpattern() throws IOException {

    MockitoAnnotations.openMocks(this);
    ObjectMapper mapper = new ObjectMapper();
    String jsonContent =
        new String(
            Files.readAllBytes(
                Paths.get(
                    "src/test/resources/importer-v1/import-scenario-with-attack-pattern.json")));
    this.importNode = mapper.readTree(jsonContent);
    this.importer.importData(
        this.importNode, Map.of(), null, null, null, null, Constants.IMPORTED_OBJECT_NAME_SUFFIX);

    Payload payload = payloadRepository.findAll().iterator().next();

    // the scenario should have one inject with one attack pattern with one killchain
    AttackPattern attackPattern = payload.getAttackPatterns().getFirst();

    KillChainPhase killChainPhase = attackPattern.getKillChainPhases().getFirst();
    assertEquals(ATTACK_PATTERN_EXTERNAL_ID, attackPattern.getExternalId());
    assertEquals(KILLCHAIN_EXTERNAL_ID, killChainPhase.getExternalId());

    // delete scenario and payload before reimporting to verify that the killchainphase is not
    // recreated
    scenarioRepository.deleteAll();
    payloadRepository.deleteAll();

    this.importer.importData(
        this.importNode, Map.of(), null, null, null, null, Constants.IMPORTED_OBJECT_NAME_SUFFIX);
    payload = payloadRepository.findAll().iterator().next();
    AttackPattern attackPattern2 = payload.getAttackPatterns().getFirst();
    KillChainPhase killChainPhase2 = attackPattern.getKillChainPhases().getFirst();

    // verify that the new payload use the same attack pattern / killchain phase
    assertEquals(attackPattern.getId(), attackPattern2.getId());
    assertEquals(killChainPhase.getId(), killChainPhase2.getId());
  }

  @Test
  @Transactional
  void
      testScenario_given_injects_nuclei_without_nuclei_injector_registered_when_starterpack_then_should_create_dummy_injector()
          throws IOException {

    MockitoAnnotations.openMocks(this);
    ObjectMapper mapper = new ObjectMapper();
    String jsonContent =
        new String(
            Files.readAllBytes(
                Paths.get(
                    "src/test/resources/importer-v1/scenario_with_injects_from_injector.json")));
    this.importNode = mapper.readTree(jsonContent);
    this.importer.importData(
        this.importNode, Map.of(), null, null, null, null, Constants.IMPORTED_OBJECT_NAME_SUFFIX);

    // dummy injector should be created with 1 associated injector contract
    Injector dummyInjector =
        this.injectorRepository.findByType(NMAP_DUMMY_INJECTOR_TYPE).orElseThrow();
    List<InjectorContract> injectorContracts =
        injectorContractRepository.findInjectorContractsByInjector(dummyInjector);
    assertEquals(1, injectorContracts.size());
  }

  // -- UTILS --

  private static Specification<Exercise> exerciseByName(@NotNull final String name) {
    return (root, query, cb) -> cb.equal(root.get("name"), name);
  }
}
