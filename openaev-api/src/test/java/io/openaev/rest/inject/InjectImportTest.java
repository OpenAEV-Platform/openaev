package io.openaev.rest.inject;

import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.rest.atomic_testing.AtomicTestingApi.ATOMIC_TESTING_URI;
import static io.openaev.rest.exercise.ExerciseApi.EXERCISE_URI;
import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static io.openaev.utils.fixtures.PayloadFixture.createDetectionRemediation;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.database.model.Tag;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.ee.Ee;
import io.openaev.rest.exercise.exports.ExportOptions;
import io.openaev.rest.inject.service.InjectExportService;
import io.openaev.service.ArticleService;
import io.openaev.service.ChallengeService;
import io.openaev.service.FileService;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.composers.*;
import io.openaev.utils.helpers.TagHelper;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@TestInstance(PER_CLASS)
@DisplayName("Importing injects tests")
class InjectImportTest extends IntegrationTest {

  String SCENARIO_IMPORT_URI = SCENARIO_URI + "/%s/injects/import";
  String SIMULATION_IMPORT_URI = EXERCISE_URI + "/%s/injects/import";
  String ATOMIC_TESTING_IMPORT_URI = ATOMIC_TESTING_URI + "/import";
  private final Map<String, ArticleComposer.Composer> staticArticleWrappers = new HashMap<>();
  private final String KNOWN_ARTICLE_WRAPPER_KEY = "known article key";

  @Autowired ObjectMapper objectMapper;
  @Autowired MockMvc mvc;
  @Autowired InjectExportService exportService;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private ArticleComposer articleComposer;
  @Autowired private ChannelComposer channelComposer;
  @Autowired private ChallengeComposer challengeComposer;
  @Autowired private DocumentComposer documentComposer;
  @Autowired private CollectorComposer collectorComposer;
  @Autowired private FileService fileService;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private TeamComposer teamComposer;
  @Autowired private UserComposer userComposer;
  @Autowired private OrganizationComposer organizationComposer;
  @Autowired private TagComposer tagComposer;
  @Autowired private DetectionRemediationComposer detectionRemediationComposer;
  @Autowired private PayloadComposer payloadComposer;
  @Autowired private DomainComposer domainComposer;
  @Autowired private ChallengeService challengeService;
  @Autowired private EntityManager entityManager;
  @Autowired private InjectRepository injectRepository;
  @Autowired private ArticleService articleService;
  @Autowired private InjectorFixture injectorFixture;
  @MockBean private Ee eeService;

  @BeforeEach
  void before() throws Exception {
    teamComposer.reset();
    userComposer.reset();
    organizationComposer.reset();
    injectComposer.reset();
    challengeComposer.reset();
    channelComposer.reset();
    articleComposer.reset();
    documentComposer.reset();
    scenarioComposer.reset();
    tagComposer.reset();
    detectionRemediationComposer.reset();
    exerciseComposer.reset();
    injectorContractComposer.reset();
    payloadComposer.reset();
    collectorComposer.reset();

    staticArticleWrappers.clear();

    for (String filename : FileFixture.WELL_KNOWN_FILES.keySet()) {
      fileService.deleteFile(filename);
    }

    clearEntityManager();
  }

  private Map<String, ArticleComposer.Composer> getStaticArticleWrappers() {
    if (!staticArticleWrappers.containsKey(KNOWN_ARTICLE_WRAPPER_KEY)) {
      staticArticleWrappers.put(
          KNOWN_ARTICLE_WRAPPER_KEY,
          articleComposer
              .forArticle(ArticleFixture.getDefaultArticle())
              .withChannel(channelComposer.forChannel(ChannelFixture.getDefaultChannel()))
              .withDocument(
                  documentComposer
                      .forDocument(DocumentFixture.getDocument(FileFixture.getPngGridFileContent()))
                      .withInMemoryFile(FileFixture.getPngGridFileContent())));
    }

    return staticArticleWrappers;
  }

  private List<InjectComposer.Composer> getInjectWrappers() {
    Set<Domain> domains = domainComposer.forDefaultToClassifyDomain().persist().getSet();

    // Inject in exercise with an article attached
    ArticleComposer.Composer articleWrapper =
        getStaticArticleWrappers().get(KNOWN_ARTICLE_WRAPPER_KEY);

    Collector collector =
        collectorComposer
            .forCollector(CollectorFixture.createDefaultCollector("CS"))
            .persist()
            .get();

    clearEntityManager();

    return List.of(
        injectComposer
            .forInject(InjectFixture.getDefaultInject())
            .withDocument(
                documentComposer
                    .forDocument(DocumentFixture.getDocument(FileFixture.getPngSmileFileContent()))
                    .withInMemoryFile(FileFixture.getPngSmileFileContent()))
            .withInjectorContract(
                injectorContractComposer
                    .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
                    .withArticle(articleWrapper))
            .withTag(tagComposer.forTag(TagFixture.getTagWithText("inject with article tag")))
            .withTeam(
                teamComposer
                    .forTeam(TeamFixture.getDefaultTeam())
                    .withOrganisation(
                        organizationComposer.forOrganization(
                            OrganizationFixture.createDefaultOrganisation()))
                    .withUser(
                        userComposer
                            .forUser(UserFixture.getUserWithDefaultEmail())
                            .withOrganization(
                                organizationComposer.forOrganization(
                                    OrganizationFixture.createDefaultOrganisation())))),
        injectComposer
            .forInject(InjectFixture.getDefaultInject())
            .withInjectorContract(
                injectorContractComposer
                    .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
                    .withChallenge(
                        challengeComposer.forChallenge(ChallengeFixture.createDefaultChallenge())))
            .withTag(tagComposer.forTag(TagFixture.getTagWithText("inject with challenge tag")))
            .withTeam(
                teamComposer
                    .forTeam(TeamFixture.getDefaultContextualTeam())
                    .withUser(userComposer.forUser(UserFixture.getUserWithDefaultEmail()))),
        injectComposer
            .forInject(InjectFixture.getDefaultInject())
            .withInjectorContract(
                injectorContractComposer
                    .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
                    .withInjector(injectorFixture.getWellKnownOaevImplantInjector())
                    .withPayload(
                        payloadComposer
                            .forPayload(PayloadFixture.createDefaultCommand(domains))
                            .withTag(
                                tagComposer.forTag(TagFixture.getTagWithText("secret payload tag")))
                            .withDetectionRemediation(
                                detectionRemediationComposer
                                    .forDetectionRemediation(createDetectionRemediation())
                                    .withCollector(collectorComposer.forCollector(collector)))))
            .withTag(tagComposer.forTag(TagFixture.getTagWithText("inject with payload tag"))),
        injectComposer
            .forInject(InjectFixture.getDefaultInject())
            .withInjectorContract(
                injectorContractComposer
                    .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
                    .withInjector(injectorFixture.getWellKnownOaevImplantInjector())
                    .withPayload(
                        payloadComposer
                            .forPayload(PayloadFixture.createDefaultFileDrop(domains))
                            .withFileDrop(
                                documentComposer
                                    .forDocument(
                                        DocumentFixture.getDocument(
                                            FileFixture.getBadCoffeeFileContent()))
                                    .withInMemoryFile(FileFixture.getBadCoffeeFileContent()))
                            .withTag(
                                tagComposer.forTag(
                                    TagFixture.getTagWithText("secret file drop tag")))))
            .withTag(
                tagComposer.forTag(TagFixture.getTagWithText("filedrop inject with payload tag"))),
        injectComposer
            .forInject(InjectFixture.getDefaultInject())
            .withInjectorContract(
                injectorContractComposer
                    .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
                    .withInjector(injectorFixture.getWellKnownOaevImplantInjector())
                    .withPayload(
                        payloadComposer
                            .forPayload(PayloadFixture.createDefaultExecutable(domains))
                            .withExecutable(
                                documentComposer
                                    .forDocument(
                                        DocumentFixture.getDocument(
                                            FileFixture.getBeadFileContent()))
                                    .withInMemoryFile(FileFixture.getBeadFileContent()))
                            .withTag(
                                tagComposer.forTag(
                                    TagFixture.getTagWithText("secret executable payload tag")))))
            .withTag(
                tagComposer.forTag(
                    TagFixture.getTagWithText("executable inject with payload tag"))));
  }

  private List<InjectComposer.Composer> getInjectFromExerciseWrappers() {
    List<InjectComposer.Composer> injectWrappers = getInjectWrappers();
    // wrap it into an exercise
    exerciseComposer
        .forExercise(ExerciseFixture.createDefaultExercise())
        .withArticle(getStaticArticleWrappers().get(KNOWN_ARTICLE_WRAPPER_KEY))
        .withInjects(injectWrappers)
        .persist();

    return injectWrappers;
  }

  private List<InjectComposer.Composer> getInjectFromScenarioWrappers() {
    List<InjectComposer.Composer> injectWrappers = getInjectWrappers();
    // wrap it into an exercise
    scenarioComposer
        .forScenario(ScenarioFixture.createDefaultCrisisScenario())
        .withArticle(getStaticArticleWrappers().get(KNOWN_ARTICLE_WRAPPER_KEY))
        .withInjects(injectWrappers)
        .persist();

    return injectWrappers;
  }

  private void clearEntityManager() {
    entityManager.flush();
    entityManager.clear();
  }

  private byte[] getExportData(
      List<InjectComposer.Composer> wrappers,
      boolean withPlayers,
      boolean withTeams,
      boolean withVariableValues)
      throws IOException {
    List<Inject> injects = wrappers.stream().map(InjectComposer.Composer::get).toList();
    byte[] data =
        exportService.exportInjectsToZip(
            injects, ExportOptions.mask(withPlayers, withTeams, withVariableValues));
    clearEntityManager();
    return data;
  }

  private ResultActions doImportForScenario(String scenarioId, byte[] importZipData)
      throws Exception {
    String uri = String.format(SCENARIO_IMPORT_URI, scenarioId);
    return doImportStringInput(uri, importZipData);
  }

  private ResultActions doImportForSimulation(String simulationId, byte[] importZipData)
      throws Exception {
    String uri = String.format(SIMULATION_IMPORT_URI, simulationId);
    return doImportStringInput(uri, importZipData);
  }

  private ResultActions doImportForAtomicTestings(byte[] importZipData) throws Exception {
    return doImportStringInput(ATOMIC_TESTING_IMPORT_URI, importZipData);
  }

  private ResultActions doImportStringInput(String uri, byte[] importZipData) throws Exception {
    ResultActions ra =
        mvc.perform(
            multipart(uri)
                .file(new MockMultipartFile("file", importZipData))
                .contentType(MediaType.MULTIPART_FORM_DATA));
    clearEntityManager();
    return ra;
  }

  private List<Inject> getImportedInjectsFromDb() {
    return injectRepository.findAll().stream()
        .filter(
            inject ->
                injectComposer.generatedItems.stream()
                    .anyMatch(
                        wrapped ->
                            wrapped.getTitle().equals(inject.getTitle())
                                && !wrapped.getId().equals(inject.getId())))
        .toList();
  }

  private List<Document> crawlDocumentsFromInjects(List<Inject> injects) throws IOException {
    List<Document> documents = new ArrayList<>();

    documents.addAll(
        injects.stream()
            .flatMap(inject -> inject.getDocuments().stream().map(InjectDocument::getDocument))
            .toList());
    documents.addAll(
        fromIterable(challengeService.getInjectsChallenges(injects)).stream()
            .flatMap(challenge -> challenge.getDocuments().stream())
            .toList());
    documents.addAll(
        articleService.getInjectsArticles(injects).stream()
            .flatMap(article -> article.getDocuments().stream())
            .toList());
    documents.addAll(
        articleService.getInjectsArticles(injects).stream()
            .map(Article::getChannel)
            .flatMap(channel -> channel.getLogos().stream())
            .toList());
    documents.addAll(
        injects.stream()
            .flatMap(
                inject -> {
                  if (inject.getPayload().isEmpty()) {
                    return Stream.of();
                  }
                  Payload pl = inject.getPayload().get();
                  return pl.getAttachedDocument().isPresent()
                      ? Stream.of(pl.getAttachedDocument().get())
                      : Stream.of();
                })
            .toList());

    return documents;
  }

  @Nested
  @WithMockUser(withCapabilities = Capability.MANAGE_ASSESSMENT)
  @DisplayName("When passing null input")
  public class WhenPassingNullInput {

    @Test
    @DisplayName("Return UNPROCESSABLE_ENTITY")
    public void returnUNPROCESSABLE_ENTITY() throws Exception {
      doImportForScenario(UUID.randomUUID().toString(), null)
          .andExpect(status().isUnprocessableEntity());
    }
  }

  @Nested
  @WithMockUser
  @DisplayName("When lacking PLANNER permissions on destination exercise")
  public class WhenLackingPLANNERPermissionsOnExercise {

    @Test
    @DisplayName("Return FORBIDDEN")
    public void returnFORBIDDEN() throws Exception {
      byte[] exportData = getExportData(getInjectFromExerciseWrappers(), false, false, false);

      Exercise targetExercise =
          exerciseComposer.forExercise(ExerciseFixture.createDefaultExercise()).persist().get();

      doImportForSimulation(targetExercise.getId(), exportData).andExpect(status().isForbidden());
    }
  }

  @Nested
  @WithMockUser(withCapabilities = Capability.MANAGE_ASSESSMENT)
  @DisplayName("When destination exercise is not found")
  public class WhenDestinationExerciseNotFound {

    @Test
    @DisplayName("Return NOT FOUND")
    public void returnNotFound() throws Exception {
      byte[] exportData = getExportData(getInjectFromExerciseWrappers(), false, false, false);

      doImportForSimulation(UUID.randomUUID().toString(), exportData)
          .andExpect(status().isNotFound());
    }
  }

  @Nested
  @WithMockUser
  @DisplayName("When lacking PLANNER permissions on scenario")
  public class WhenLackingPLANNERPermissionsOnScenario {

    @Test
    @DisplayName("Return FORBIDDEN")
    public void returnFORBIDDEN() throws Exception {
      byte[] exportData = getExportData(getInjectFromExerciseWrappers(), false, false, false);

      Scenario targetScenario =
          scenarioComposer
              .forScenario(ScenarioFixture.createDefaultIncidentResponseScenario())
              .persist()
              .get();

      doImportForScenario(targetScenario.getId(), exportData).andExpect(status().isForbidden());
    }
  }

  @Nested
  @WithMockUser(withCapabilities = Capability.MANAGE_ASSESSMENT)
  @DisplayName("When destination scenario is not found")
  public class WhenDestinationScenarioNotFound {

    @Test
    @DisplayName("Return NOT FOUND")
    public void returnNotFound() throws Exception {
      byte[] exportData = getExportData(getInjectFromExerciseWrappers(), false, false, false);

      doImportForScenario(UUID.randomUUID().toString(), exportData)
          .andExpect(status().isNotFound());
    }
  }

  @Nested
  @WithMockUser
  @DisplayName("When lacking ADMIN permissions for atomic testings")
  public class WhenLackingADMINPermissionsForAtomicTests {

    @Test
    @DisplayName("Return FORBIDDEN")
    public void returnFORBIDDEN() throws Exception {
      byte[] exportData = getExportData(getInjectFromExerciseWrappers(), false, false, false);

      doImportForAtomicTestings(exportData).andExpect(status().isForbidden());
    }
  }

  @Nested
  @WithMockUser(withCapabilities = {Capability.MANAGE_ASSESSMENT})
  @DisplayName("When imported objects don't already exist on the destination")
  public class WhenImportedObjectsDontAlreadyExistOnDestination {

    private byte[] getExportDataThenDelete(List<InjectComposer.Composer> wrappers)
        throws IOException {
      return getExportDataThenDelete(wrappers, false, false, false);
    }

    private byte[] getExportDataThenDelete(
        List<InjectComposer.Composer> wrappers,
        boolean withPlayers,
        boolean withTeams,
        boolean withVariableValues)
        throws IOException {
      byte[] data = getExportData(wrappers, withPlayers, withTeams, withVariableValues);
      wrappers.forEach(InjectComposer.Composer::delete);
      return data;
    }

    @Nested
    @DisplayName("When targeting an exercise")
    public class WhenTargetingAnExercise {

      private ExerciseComposer.Composer getPersistedExerciseWrapper() {
        return exerciseComposer.forExercise(ExerciseFixture.createDefaultExercise()).persist();
      }

      @Test
      @DisplayName("All injects were appended to destination")
      public void allInjectsWereAppendedToDestination() throws Exception {
        byte[] exportData =
            getExportDataThenDelete(getInjectFromExerciseWrappers(), true, true, true);
        ExerciseComposer.Composer destinationExerciseWrapper = getPersistedExerciseWrapper();

        doImportForSimulation(destinationExerciseWrapper.get().getId(), exportData)
            .andExpect(status().is2xxSuccessful());

        for (Inject expected : injectComposer.generatedItems) {
          Exercise dest =
              exerciseRepository.findById(destinationExerciseWrapper.get().getId()).orElseThrow();
          Optional<Inject> recreated =
              dest.getInjects().stream()
                  .filter(i -> i.getTitle().equals(expected.getTitle()))
                  .findAny();

          Assertions.assertTrue(recreated.isPresent(), "Could not find expected inject");
          Assertions.assertEquals(expected.getCity(), recreated.get().getCity());
          Assertions.assertEquals(expected.getCountry(), recreated.get().getCountry());
          Assertions.assertEquals(
              expected.getDependsDuration(), recreated.get().getDependsDuration());
          Assertions.assertEquals(
              expected.getNumberOfTargetUsers(), recreated.get().getNumberOfTargetUsers());
          Assertions.assertEquals(expected.getDescription(), recreated.get().getDescription());

          // the challenge ID is necessarily different from source and imported values, therefore
          // ignore
          // this
          assertThatJson(recreated.get().getContent())
              .whenIgnoringPaths("challenges", "articles")
              .isEqualTo(expected.getContent());

          Assertions.assertNotEquals(expected.getId(), recreated.get().getId());
        }
      }

      @Test
      @DisplayName("All articles have been recreated")
      public void allArticlesHaveBeenRecreated() throws Exception {
        byte[] exportData =
            getExportDataThenDelete(getInjectFromExerciseWrappers(), true, true, true);
        ExerciseComposer.Composer destinationExerciseWrapper = getPersistedExerciseWrapper();
        doImportForSimulation(destinationExerciseWrapper.get().getId(), exportData)
            .andExpect(status().is2xxSuccessful());
        clearEntityManager();

        for (Article expected : articleComposer.generatedItems) {
          Exercise dest =
              exerciseRepository.findById(destinationExerciseWrapper.get().getId()).orElseThrow();
          Optional<Article> recreated =
              dest.getArticles().stream()
                  .filter(a -> a.getName().equals(expected.getName()))
                  .findFirst();

          Assertions.assertTrue(recreated.isPresent(), "Could not find expected article");
          Assertions.assertEquals(expected.getName(), recreated.get().getName());
          Assertions.assertEquals(expected.getAuthor(), recreated.get().getAuthor());
          Assertions.assertEquals(expected.getComments(), recreated.get().getComments());
          Assertions.assertEquals(expected.getLikes(), recreated.get().getLikes());
          Assertions.assertEquals(expected.getContent(), recreated.get().getContent());
          Assertions.assertEquals(expected.getShares(), recreated.get().getShares());
          Assertions.assertEquals(
              expected.getVirtualPublication(), recreated.get().getVirtualPublication());

          Assertions.assertNotEquals(expected.getId(), recreated.get().getId());
        }
      }

      @Test
      @DisplayName("All channels have been recreated")
      public void allChannelsHaveBeenRecreated() throws Exception {
        byte[] exportData =
            getExportDataThenDelete(getInjectFromExerciseWrappers(), true, true, true);
        ExerciseComposer.Composer destinationExerciseWrapper = getPersistedExerciseWrapper();
        doImportForSimulation(destinationExerciseWrapper.get().getId(), exportData)
            .andExpect(status().is2xxSuccessful());
        clearEntityManager();

        for (Channel expected : channelComposer.generatedItems) {
          Exercise dest =
              exerciseRepository.findById(destinationExerciseWrapper.get().getId()).orElseThrow();
          Optional<Channel> recreated =
              dest.getArticles().stream()
                  .map(Article::getChannel)
                  .filter(c -> c.getName().equals(expected.getName()))
                  .findAny();

          Assertions.assertTrue(recreated.isPresent(), "Could not find expected channel");
          Assertions.assertEquals(expected.getName(), recreated.get().getName());
          Assertions.assertEquals(expected.getDescription(), recreated.get().getDescription());
          Assertions.assertEquals(expected.getType(), recreated.get().getType());
          Assertions.assertEquals(expected.getMode(), recreated.get().getMode());
          Assertions.assertEquals(
              expected.getPrimaryColorDark(), recreated.get().getPrimaryColorDark());
          Assertions.assertEquals(
              expected.getSecondaryColorDark(), recreated.get().getSecondaryColorDark());
          Assertions.assertEquals(
              expected.getPrimaryColorLight(), recreated.get().getPrimaryColorLight());
          Assertions.assertEquals(
              expected.getSecondaryColorLight(), recreated.get().getSecondaryColorLight());

          Assertions.assertNotEquals(expected.getId(), recreated.get().getId());
        }
      }

      @Test
      @DisplayName("All challenges have been recreated")
      public void allChallengesHaveBeenRecreated() throws Exception {
        byte[] exportData =
            getExportDataThenDelete(getInjectFromExerciseWrappers(), true, true, true);
        ExerciseComposer.Composer destinationExerciseWrapper = getPersistedExerciseWrapper();
        doImportForSimulation(destinationExerciseWrapper.get().getId(), exportData)
            .andExpect(status().is2xxSuccessful());
        clearEntityManager();

        for (Challenge expected : challengeComposer.generatedItems) {
          Exercise dest =
              exerciseRepository.findById(destinationExerciseWrapper.get().getId()).orElseThrow();
          Optional<Challenge> recreated =
              fromIterable(challengeService.getExerciseChallenges(dest.getId())).stream()
                  .filter(c -> c.getName().equals(expected.getName()))
                  .findAny();

          Assertions.assertTrue(recreated.isPresent(), "Could not find expected challenge");
          Assertions.assertEquals(expected.getName(), recreated.get().getName());
          Assertions.assertEquals(expected.getContent(), recreated.get().getContent());
          Assertions.assertEquals(expected.getCategory(), recreated.get().getCategory());
          Assertions.assertEquals(expected.getScore(), recreated.get().getScore());
          Assertions.assertEquals(expected.getMaxAttempts(), recreated.get().getMaxAttempts());
          for (ChallengeFlag flag : expected.getFlags()) {
            Assertions.assertTrue(
                recreated.get().getFlags().stream()
                    .anyMatch(
                        flg ->
                            flg.getType().equals(flag.getType())
                                && flg.getValue().equals(flag.getValue())),
                "Flag of type " + flag.getType() + " not found in challenge");

            Assertions.assertNotEquals(expected.getId(), recreated.get().getId());
          }
        }
      }

      @Test
      @DisplayName("All payloads have been recreated")
      public void allPayloadsHaveBeenRecreated() throws Exception {

        // If We want to include detection remediations we need to have a licence
        when(eeService.isEnterpriseLicenseInactive(any())).thenReturn(false);

        byte[] exportData =
            getExportDataThenDelete(getInjectFromExerciseWrappers(), true, true, true);
        ExerciseComposer.Composer destinationExerciseWrapper = getPersistedExerciseWrapper();

        // We need to save the collector to check the import
        collectorComposer.forCollector(CollectorFixture.createDefaultCollector("CS")).persist();

        doImportForSimulation(destinationExerciseWrapper.get().getId(), exportData)
            .andExpect(status().is2xxSuccessful());
        clearEntityManager();

        for (Payload expected : payloadComposer.generatedItems) {
          Exercise dest =
              exerciseRepository.findById(destinationExerciseWrapper.get().getId()).orElseThrow();
          Optional<Payload> recreated =
              dest.getInjects().stream()
                  .map(Inject::getInjectorContract)
                  .filter(Optional::isPresent)
                  .map(injectorContract -> injectorContract.get().getPayload())
                  .filter(Objects::nonNull)
                  .filter(c -> c.getName().equals(expected.getName()))
                  .findAny();

          Assertions.assertTrue(recreated.isPresent(), "Could not find expected payload");
          Assertions.assertEquals(expected.getName(), recreated.get().getName());
          Assertions.assertEquals(expected.getDescription(), recreated.get().getDescription());
          Assertions.assertEquals(expected.getStatus(), recreated.get().getStatus());
          Assertions.assertEquals(
              expected.getCleanupCommand(), recreated.get().getCleanupCommand());
          Assertions.assertEquals(
              expected.getCleanupExecutor(), recreated.get().getCleanupExecutor());
          Assertions.assertEquals(expected.getExecutionArch(), recreated.get().getExecutionArch());
          Assertions.assertEquals(expected.getType(), recreated.get().getType());
          Assertions.assertEquals(expected.getSource(), recreated.get().getSource());
          Assertions.assertEquals(expected.getExternalId(), recreated.get().getExternalId());

          Assertions.assertNotEquals(expected.getId(), recreated.get().getId());
          Assertions.assertEquals(
              expected.getDetectionRemediations().size(),
              recreated.get().getDetectionRemediations().size());

          if (!expected.getDetectionRemediations().isEmpty()) {
            Assertions.assertEquals(
                expected.getDetectionRemediations().get(0).getValues(),
                recreated.get().getDetectionRemediations().get(0).getValues());
          }
        }
      }

      @Test
      @DisplayName("All teams have been recreated")
      public void allTeamsHaveBeenRecreated() throws Exception {
        byte[] exportData =
            getExportDataThenDelete(getInjectFromExerciseWrappers(), true, true, true);
        ExerciseComposer.Composer destinationExerciseWrapper = getPersistedExerciseWrapper();
        doImportForSimulation(destinationExerciseWrapper.get().getId(), exportData)
            .andExpect(status().is2xxSuccessful());
        clearEntityManager();

        for (Team expected : teamComposer.generatedItems) {
          Exercise dest =
              exerciseRepository.findById(destinationExerciseWrapper.get().getId()).orElseThrow();
          Optional<Team> recreated =
              dest.getTeams().stream()
                  .filter(c -> c.getName().equals(expected.getName()))
                  .findAny();

          Assertions.assertTrue(recreated.isPresent(), "Could not find expected team");
          Assertions.assertEquals(expected.getName(), recreated.get().getName());
          Assertions.assertEquals(expected.getDescription(), recreated.get().getDescription());
          Assertions.assertEquals(expected.getContextual(), recreated.get().getContextual());

          Assertions.assertNotEquals(expected.getId(), recreated.get().getId());
        }
      }

      @Test
      @DisplayName("All users have been recreated")
      public void allUsersHaveBeenRecreated() throws Exception {
        byte[] exportData =
            getExportDataThenDelete(getInjectFromExerciseWrappers(), true, true, true);
        ExerciseComposer.Composer destinationExerciseWrapper = getPersistedExerciseWrapper();
        doImportForSimulation(destinationExerciseWrapper.get().getId(), exportData)
            .andExpect(status().is2xxSuccessful());
        clearEntityManager();

        for (User expected : userComposer.generatedItems) {
          Exercise dest =
              exerciseRepository.findById(destinationExerciseWrapper.get().getId()).orElseThrow();
          Optional<User> recreated =
              dest.getTeams().stream()
                  .flatMap(team -> team.getUsers().stream())
                  .filter(c -> c.getEmail().equals(expected.getEmail()))
                  .findAny();

          Assertions.assertTrue(recreated.isPresent(), "Could not find expected user");
          Assertions.assertEquals(expected.getName(), recreated.get().getName());
          Assertions.assertEquals(expected.getFirstname(), recreated.get().getFirstname());
          Assertions.assertEquals(expected.getLastname(), recreated.get().getLastname());
          Assertions.assertEquals(expected.getLang(), recreated.get().getLang());
          Assertions.assertEquals(expected.getEmail(), recreated.get().getEmail());
          Assertions.assertEquals(expected.getPhone(), recreated.get().getPhone());
          Assertions.assertEquals(expected.getPgpKey(), recreated.get().getPgpKey());
          Assertions.assertEquals(expected.getCountry(), recreated.get().getCountry());
          Assertions.assertEquals(expected.getCity(), recreated.get().getCity());

          Assertions.assertNotEquals(expected.getId(), recreated.get().getId());
        }
      }

      @Test
      @DisplayName("All organisations have been recreated")
      public void allOrganisationsHaveBeenRecreated() throws Exception {
        byte[] exportData =
            getExportDataThenDelete(getInjectFromExerciseWrappers(), true, true, true);
        ExerciseComposer.Composer destinationExerciseWrapper = getPersistedExerciseWrapper();
        doImportForSimulation(destinationExerciseWrapper.get().getId(), exportData)
            .andExpect(status().is2xxSuccessful());
        clearEntityManager();

        for (Organization expected : organizationComposer.generatedItems) {
          Exercise dest =
              exerciseRepository.findById(destinationExerciseWrapper.get().getId()).orElseThrow();
          List<Organization> orgs = new ArrayList<>();
          orgs.addAll(
              dest.getInjects().stream()
                  .flatMap(inject -> inject.getTeams().stream())
                  .flatMap(team -> team.getUsers().stream())
                  .map(User::getOrganization)
                  .filter(Objects::nonNull)
                  .toList());
          orgs.addAll(
              dest.getInjects().stream()
                  .flatMap(inject -> inject.getTeams().stream())
                  .map(Team::getOrganization)
                  .filter(Objects::nonNull)
                  .toList());
          Optional<Organization> recreated =
              orgs.stream().filter(c -> c.getName().equals(expected.getName())).findAny();

          Assertions.assertTrue(recreated.isPresent(), "Could not find expected organisation");
          Assertions.assertEquals(expected.getName(), recreated.get().getName());
          Assertions.assertEquals(expected.getDescription(), recreated.get().getDescription());

          Assertions.assertNotEquals(expected.getId(), recreated.get().getId());
        }
      }

      @Test
      @DisplayName("All tags have been recreated")
      public void allTagsHaveBeenRecreated() throws Exception {
        byte[] exportData =
            getExportDataThenDelete(getInjectFromExerciseWrappers(), true, true, true);
        ExerciseComposer.Composer destinationExerciseWrapper = getPersistedExerciseWrapper();
        doImportForSimulation(destinationExerciseWrapper.get().getId(), exportData)
            .andExpect(status().is2xxSuccessful());
        clearEntityManager();

        for (Tag expected : tagComposer.generatedItems) {
          Exercise dest =
              exerciseRepository.findById(destinationExerciseWrapper.get().getId()).orElseThrow();

          Optional<Tag> recreated =
              TagHelper.crawlAllExerciseTags(dest, challengeService).stream()
                  .filter(c -> c.getName().equals(expected.getName()))
                  .findAny();

          Assertions.assertTrue(
              recreated.isPresent(),
              "Could not find expected tag '%s'".formatted(expected.getName()));
          Assertions.assertEquals(expected.getName(), recreated.get().getName());
          Assertions.assertEquals(expected.getColor(), recreated.get().getColor());

          Assertions.assertNotEquals(expected.getId(), recreated.get().getId());
        }
      }

      @Test
      @DisplayName("All documents have been recreated")
      public void allDocumentsHaveBeenRecreated() throws Exception {
        byte[] exportData =
            getExportDataThenDelete(getInjectFromExerciseWrappers(), true, true, true);
        ExerciseComposer.Composer destinationExerciseWrapper = getPersistedExerciseWrapper();
        doImportForSimulation(destinationExerciseWrapper.get().getId(), exportData)
            .andExpect(status().is2xxSuccessful());
        clearEntityManager();

        for (Document expected : documentComposer.generatedItems) {
          Exercise dest =
              exerciseRepository.findById(destinationExerciseWrapper.get().getId()).orElseThrow();
          Optional<Document> recreated =
              crawlDocumentsFromInjects(dest.getInjects()).stream()
                  .filter(c -> c.getName().equals(expected.getName()))
                  .findAny();

          Assertions.assertTrue(
              recreated.isPresent(),
              "Could not find expected document %s".formatted(expected.getTarget()));
          Assertions.assertEquals(expected.getName(), recreated.get().getName());
          Assertions.assertEquals(expected.getDescription(), recreated.get().getDescription());
          Assertions.assertEquals(expected.getTarget(), recreated.get().getTarget());
          Assertions.assertEquals(expected.getType(), recreated.get().getType());

          Assertions.assertNotEquals(expected.getId(), recreated.get().getId());
        }
      }
    }

    @Nested
    @DisplayName("When targeting a scenario")
    public class WhenTargetingAScenario {

      private ScenarioComposer.Composer getPersistedScenarioWrapper() {
        return scenarioComposer
            .forScenario(ScenarioFixture.createDefaultIncidentResponseScenario())
            .persist();
      }

      @Test
      @DisplayName("All injects were appended to destination")
      public void allInjectsWereAppendedToDestination() throws Exception {
        byte[] exportData =
            getExportDataThenDelete(getInjectFromExerciseWrappers(), true, true, true);
        ScenarioComposer.Composer destinationScenarioWrapper = getPersistedScenarioWrapper();
        doImportForScenario(destinationScenarioWrapper.get().getId(), exportData)
            .andExpect(status().is2xxSuccessful());
        clearEntityManager();

        for (Inject expected : injectComposer.generatedItems) {
          Scenario dest =
              scenarioRepository.findById(destinationScenarioWrapper.get().getId()).orElseThrow();
          Optional<Inject> recreated =
              dest.getInjects().stream()
                  .filter(i -> i.getTitle().equals(expected.getTitle()))
                  .findAny();

          Assertions.assertTrue(recreated.isPresent(), "Could not find expected inject");
          Assertions.assertEquals(expected.getCity(), recreated.get().getCity());
          Assertions.assertEquals(expected.getCountry(), recreated.get().getCountry());
          Assertions.assertEquals(
              expected.getDependsDuration(), recreated.get().getDependsDuration());
          Assertions.assertEquals(
              expected.getNumberOfTargetUsers(), recreated.get().getNumberOfTargetUsers());
          Assertions.assertEquals(expected.getDescription(), recreated.get().getDescription());

          // the challenge ID is necessarily different from source and imported values, therefore
          // ignore
          // this
          assertThatJson(recreated.get().getContent())
              .whenIgnoringPaths("challenges", "articles")
              .isEqualTo(expected.getContent());

          Assertions.assertNotEquals(expected.getId(), recreated.get().getId());
        }
      }

      @Test
      @DisplayName("All articles have been recreated")
      public void allArticlesHaveBeenRecreated() throws Exception {
        byte[] exportData =
            getExportDataThenDelete(getInjectFromScenarioWrappers(), true, true, true);
        ScenarioComposer.Composer destinationScenarioWrapper = getPersistedScenarioWrapper();
        doImportForScenario(destinationScenarioWrapper.get().getId(), exportData)
            .andExpect(status().is2xxSuccessful());
        clearEntityManager();

        for (Article expected : articleComposer.generatedItems) {
          Scenario dest =
              scenarioRepository.findById(destinationScenarioWrapper.get().getId()).orElseThrow();
          Optional<Article> recreated =
              dest.getArticles().stream()
                  .filter(a -> a.getName().equals(expected.getName()))
                  .findFirst();

          Assertions.assertTrue(recreated.isPresent(), "Could not find expected article");
          Assertions.assertEquals(expected.getName(), recreated.get().getName());
          Assertions.assertEquals(expected.getAuthor(), recreated.get().getAuthor());
          Assertions.assertEquals(expected.getComments(), recreated.get().getComments());
          Assertions.assertEquals(expected.getLikes(), recreated.get().getLikes());
          Assertions.assertEquals(expected.getContent(), recreated.get().getContent());
          Assertions.assertEquals(expected.getShares(), recreated.get().getShares());
          Assertions.assertEquals(
              expected.getVirtualPublication(), recreated.get().getVirtualPublication());

          Assertions.assertNotEquals(expected.getId(), recreated.get().getId());
        }
      }

      @Test
      @DisplayName("All channels have been recreated")
      public void allChannelsHaveBeenRecreated() throws Exception {
        byte[] exportData =
            getExportDataThenDelete(getInjectFromScenarioWrappers(), true, true, true);
        ScenarioComposer.Composer destinationScenarioWrapper = getPersistedScenarioWrapper();
        doImportForScenario(destinationScenarioWrapper.get().getId(), exportData)
            .andExpect(status().is2xxSuccessful());
        clearEntityManager();

        for (Channel expected : channelComposer.generatedItems) {
          Scenario dest =
              scenarioRepository.findById(destinationScenarioWrapper.get().getId()).orElseThrow();
          Optional<Channel> recreated =
              dest.getArticles().stream()
                  .map(Article::getChannel)
                  .filter(c -> c.getName().equals(expected.getName()))
                  .findAny();

          Assertions.assertTrue(recreated.isPresent(), "Could not find expected channel");
          Assertions.assertEquals(expected.getName(), recreated.get().getName());
          Assertions.assertEquals(expected.getDescription(), recreated.get().getDescription());
          Assertions.assertEquals(expected.getType(), recreated.get().getType());
          Assertions.assertEquals(expected.getMode(), recreated.get().getMode());
          Assertions.assertEquals(
              expected.getPrimaryColorDark(), recreated.get().getPrimaryColorDark());
          Assertions.assertEquals(
              expected.getSecondaryColorDark(), recreated.get().getSecondaryColorDark());
          Assertions.assertEquals(
              expected.getPrimaryColorLight(), recreated.get().getPrimaryColorLight());
          Assertions.assertEquals(
              expected.getSecondaryColorLight(), recreated.get().getSecondaryColorLight());

          Assertions.assertNotEquals(expected.getId(), recreated.get().getId());
        }
      }

      @Test
      @DisplayName("All challenges have been recreated")
      public void allChallengesHaveBeenRecreated() throws Exception {
        byte[] exportData =
            getExportDataThenDelete(getInjectFromScenarioWrappers(), true, true, true);
        ScenarioComposer.Composer destinationScenarioWrapper = getPersistedScenarioWrapper();
        doImportForScenario(destinationScenarioWrapper.get().getId(), exportData)
            .andExpect(status().is2xxSuccessful());
        clearEntityManager();

        for (Challenge expected : challengeComposer.generatedItems) {
          Scenario dest =
              scenarioRepository.findById(destinationScenarioWrapper.get().getId()).orElseThrow();
          Optional<Challenge> recreated =
              fromIterable(challengeService.getScenarioChallenges(dest)).stream()
                  .filter(c -> c.getName().equals(expected.getName()))
                  .findAny();

          Assertions.assertTrue(recreated.isPresent(), "Could not find expected challenge");
          Assertions.assertEquals(expected.getName(), recreated.get().getName());
          Assertions.assertEquals(expected.getContent(), recreated.get().getContent());
          Assertions.assertEquals(expected.getCategory(), recreated.get().getCategory());
          Assertions.assertEquals(expected.getScore(), recreated.get().getScore());
          Assertions.assertEquals(expected.getMaxAttempts(), recreated.get().getMaxAttempts());
          for (ChallengeFlag flag : expected.getFlags()) {
            Assertions.assertTrue(
                recreated.get().getFlags().stream()
                    .anyMatch(
                        flg ->
                            flg.getType().equals(flag.getType())
                                && flg.getValue().equals(flag.getValue())),
                "Flag of type " + flag.getType() + " not found in challenge");

            Assertions.assertNotEquals(expected.getId(), recreated.get().getId());
          }
        }
      }

      @Test
      @DisplayName("All payloads have been recreated")
      public void allPayloadsHaveBeenRecreated() throws Exception {

        // If We want to include detection remediations we need to have a licence
        when(eeService.isEnterpriseLicenseInactive(any())).thenReturn(false);

        byte[] exportData =
            getExportDataThenDelete(getInjectFromScenarioWrappers(), true, true, true);
        ScenarioComposer.Composer destinationScenarioWrapper = getPersistedScenarioWrapper();

        // We need to save the collector to check the import
        collectorComposer.forCollector(CollectorFixture.createDefaultCollector("CS")).persist();

        doImportForScenario(destinationScenarioWrapper.get().getId(), exportData)
            .andExpect(status().is2xxSuccessful());
        clearEntityManager();

        for (Payload expected : payloadComposer.generatedItems) {
          Scenario dest =
              scenarioRepository.findById(destinationScenarioWrapper.get().getId()).orElseThrow();
          Optional<Payload> recreated =
              dest.getInjects().stream()
                  .map(Inject::getInjectorContract)
                  .filter(Optional::isPresent)
                  .map(injectorContract -> injectorContract.get().getPayload())
                  .filter(Objects::nonNull)
                  .filter(c -> c.getName().equals(expected.getName()))
                  .findAny();

          Assertions.assertTrue(recreated.isPresent(), "Could not find expected payload");
          Assertions.assertEquals(expected.getName(), recreated.get().getName());
          Assertions.assertEquals(expected.getDescription(), recreated.get().getDescription());
          Assertions.assertEquals(expected.getStatus(), recreated.get().getStatus());
          Assertions.assertEquals(
              expected.getCleanupCommand(), recreated.get().getCleanupCommand());
          Assertions.assertEquals(
              expected.getCleanupExecutor(), recreated.get().getCleanupExecutor());
          Assertions.assertEquals(expected.getExecutionArch(), recreated.get().getExecutionArch());
          Assertions.assertEquals(expected.getType(), recreated.get().getType());
          Assertions.assertEquals(expected.getSource(), recreated.get().getSource());
          Assertions.assertEquals(expected.getExternalId(), recreated.get().getExternalId());

          if (Objects.equals(expected.getType(), FileDrop.FILE_DROP_TYPE)) {
            Assertions.assertNotNull(((FileDrop) recreated.get()).getFileDropFile());
          }

          if (Objects.equals(expected.getType(), Executable.EXECUTABLE_TYPE)) {
            Assertions.assertNotNull(((Executable) recreated.get()).getExecutableFile());
          }

          Assertions.assertNotEquals(expected.getId(), recreated.get().getId());

          Assertions.assertEquals(
              expected.getDetectionRemediations().size(),
              recreated.get().getDetectionRemediations().size());

          if (!expected.getDetectionRemediations().isEmpty()) {
            Assertions.assertEquals(
                expected.getDetectionRemediations().get(0).getValues(),
                recreated.get().getDetectionRemediations().get(0).getValues());
          }
        }
      }

      @Test
      @DisplayName("All teams have been recreated")
      public void allTeamsHaveBeenRecreated() throws Exception {
        byte[] exportData =
            getExportDataThenDelete(getInjectFromScenarioWrappers(), true, true, true);
        ScenarioComposer.Composer destinationScenarioWrapper = getPersistedScenarioWrapper();
        doImportForScenario(destinationScenarioWrapper.get().getId(), exportData)
            .andExpect(status().isOk());
        clearEntityManager();

        for (Team expected : teamComposer.generatedItems) {
          Scenario dest =
              scenarioRepository.findById(destinationScenarioWrapper.get().getId()).orElseThrow();
          Optional<Team> recreated =
              dest.getTeams().stream()
                  .filter(c -> c.getName().equals(expected.getName()))
                  .findAny();

          Assertions.assertTrue(recreated.isPresent(), "Could not find expected team");
          Assertions.assertEquals(expected.getName(), recreated.get().getName());
          Assertions.assertEquals(expected.getDescription(), recreated.get().getDescription());
          Assertions.assertEquals(expected.getContextual(), recreated.get().getContextual());

          Assertions.assertNotEquals(expected.getId(), recreated.get().getId());
        }
      }

      @Test
      @DisplayName("All users have been recreated")
      public void allUsersHaveBeenRecreated() throws Exception {
        byte[] exportData =
            getExportDataThenDelete(getInjectFromScenarioWrappers(), true, true, true);
        ScenarioComposer.Composer destinationScenarioWrapper = getPersistedScenarioWrapper();
        doImportForScenario(destinationScenarioWrapper.get().getId(), exportData)
            .andExpect(status().is2xxSuccessful());
        clearEntityManager();

        for (User expected : userComposer.generatedItems) {
          Scenario dest =
              scenarioRepository.findById(destinationScenarioWrapper.get().getId()).orElseThrow();
          Optional<User> recreated =
              dest.getTeams().stream()
                  .flatMap(team -> team.getUsers().stream())
                  .filter(c -> c.getEmail().equals(expected.getEmail()))
                  .findAny();

          Assertions.assertTrue(recreated.isPresent(), "Could not find expected user");
          Assertions.assertEquals(expected.getName(), recreated.get().getName());
          Assertions.assertEquals(expected.getFirstname(), recreated.get().getFirstname());
          Assertions.assertEquals(expected.getLastname(), recreated.get().getLastname());
          Assertions.assertEquals(expected.getLang(), recreated.get().getLang());
          Assertions.assertEquals(expected.getEmail(), recreated.get().getEmail());
          Assertions.assertEquals(expected.getPhone(), recreated.get().getPhone());
          Assertions.assertEquals(expected.getPgpKey(), recreated.get().getPgpKey());
          Assertions.assertEquals(expected.getCountry(), recreated.get().getCountry());
          Assertions.assertEquals(expected.getCity(), recreated.get().getCity());

          Assertions.assertNotEquals(expected.getId(), recreated.get().getId());
        }
      }

      @Test
      @DisplayName("All organisations have been recreated")
      public void allOrganisationsHaveBeenRecreated() throws Exception {
        byte[] exportData =
            getExportDataThenDelete(getInjectFromScenarioWrappers(), true, true, true);
        ScenarioComposer.Composer destinationScenarioWrapper = getPersistedScenarioWrapper();
        doImportForScenario(destinationScenarioWrapper.get().getId(), exportData)
            .andExpect(status().is2xxSuccessful());
        clearEntityManager();

        for (Organization expected : organizationComposer.generatedItems) {
          Scenario dest =
              scenarioRepository.findById(destinationScenarioWrapper.get().getId()).orElseThrow();
          List<Organization> orgs = new ArrayList<>();
          orgs.addAll(
              dest.getInjects().stream()
                  .flatMap(inject -> inject.getTeams().stream())
                  .flatMap(team -> team.getUsers().stream())
                  .map(User::getOrganization)
                  .filter(Objects::nonNull)
                  .toList());
          orgs.addAll(
              dest.getInjects().stream()
                  .flatMap(inject -> inject.getTeams().stream())
                  .map(Team::getOrganization)
                  .filter(Objects::nonNull)
                  .toList());
          Optional<Organization> recreated =
              orgs.stream().filter(c -> c.getName().equals(expected.getName())).findAny();

          Assertions.assertTrue(recreated.isPresent(), "Could not find expected user");
          Assertions.assertEquals(expected.getName(), recreated.get().getName());
          Assertions.assertEquals(expected.getDescription(), recreated.get().getDescription());

          Assertions.assertNotEquals(expected.getId(), recreated.get().getId());
        }
      }

      @Test
      @DisplayName("All tags have been recreated")
      public void allTagsHaveBeenRecreated() throws Exception {
        byte[] exportData =
            getExportDataThenDelete(getInjectFromScenarioWrappers(), true, true, true);
        ScenarioComposer.Composer destinationScenarioWrapper = getPersistedScenarioWrapper();
        doImportForScenario(destinationScenarioWrapper.get().getId(), exportData)
            .andExpect(status().is2xxSuccessful());
        clearEntityManager();

        for (Tag expected : tagComposer.generatedItems) {
          Scenario dest =
              scenarioRepository.findById(destinationScenarioWrapper.get().getId()).orElseThrow();

          Optional<Tag> recreated =
              TagHelper.crawlAllScenarioTags(dest, challengeService).stream()
                  .filter(c -> c.getName().equals(expected.getName()))
                  .findAny();

          Assertions.assertTrue(
              recreated.isPresent(),
              "Could not find expected tag '%s'".formatted(expected.getName()));
          Assertions.assertEquals(expected.getName(), recreated.get().getName());
          Assertions.assertEquals(expected.getColor(), recreated.get().getColor());

          Assertions.assertNotEquals(expected.getId(), recreated.get().getId());
        }
      }

      @Test
      @DisplayName("All documents have been recreated")
      public void allDocumentsHaveBeenRecreated() throws Exception {
        byte[] exportData =
            getExportDataThenDelete(getInjectFromScenarioWrappers(), true, true, true);
        ScenarioComposer.Composer destinationScenarioWrapper = getPersistedScenarioWrapper();
        doImportForScenario(destinationScenarioWrapper.get().getId(), exportData)
            .andExpect(status().is2xxSuccessful());
        clearEntityManager();

        for (Document expected : documentComposer.generatedItems) {
          Scenario dest =
              scenarioRepository.findById(destinationScenarioWrapper.get().getId()).orElseThrow();
          Optional<Document> recreated =
              crawlDocumentsFromInjects(dest.getInjects()).stream()
                  .filter(c -> c.getName().equals(expected.getName()))
                  .findAny();

          Assertions.assertTrue(recreated.isPresent(), "Could not find expected document");
          Assertions.assertEquals(expected.getName(), recreated.get().getName());
          Assertions.assertEquals(expected.getDescription(), recreated.get().getDescription());
          Assertions.assertEquals(expected.getTarget(), recreated.get().getTarget());
          Assertions.assertEquals(expected.getType(), recreated.get().getType());

          Assertions.assertNotEquals(expected.getId(), recreated.get().getId());
        }
      }
    }

    @Nested
    @WithMockUser(isAdmin = true)
    @DisplayName("When targeting atomic testing")
    public class WhenTargetingAtomicTesting {

      @Test
      @DisplayName("Each inject was created in its own atomic testing")
      public void eachInjectWasCreatedInAtomicTesting() throws Exception {
        byte[] exportData =
            getExportDataThenDelete(getInjectFromExerciseWrappers(), true, true, true);

        doImportForAtomicTestings(exportData).andExpect(status().is2xxSuccessful());
        clearEntityManager();

        for (Inject expected : injectComposer.generatedItems) {
          Optional<Inject> recreated =
              getImportedInjectsFromDb().stream()
                  .filter(i -> i.getTitle().equals(expected.getTitle()))
                  .findAny();

          Assertions.assertTrue(recreated.isPresent(), "Could not find expected inject");
          Assertions.assertEquals(expected.getCity(), recreated.get().getCity());
          Assertions.assertEquals(expected.getCountry(), recreated.get().getCountry());
          Assertions.assertEquals(
              expected.getDependsDuration(), recreated.get().getDependsDuration());
          Assertions.assertEquals(
              expected.getNumberOfTargetUsers(), recreated.get().getNumberOfTargetUsers());
          Assertions.assertEquals(expected.getDescription(), recreated.get().getDescription());

          // the challenge ID is necessarily different from source and imported values, therefore
          // ignore
          // this
          assertThatJson(recreated.get().getContent())
              .whenIgnoringPaths("challenges", "articles")
              .isEqualTo(expected.getContent());

          Assertions.assertNotEquals(expected.getId(), recreated.get().getId());
        }
      }

      @Test
      @DisplayName("All payloads have been recreated")
      public void allPayloadsHaveBeenRecreated() throws Exception {

        // If We want to include detection remediations we need to have a licence
        when(eeService.isEnterpriseLicenseInactive(any())).thenReturn(false);

        byte[] exportData =
            getExportDataThenDelete(getInjectFromScenarioWrappers(), true, true, true);

        // We need to save the collector to check the import
        collectorComposer.forCollector(CollectorFixture.createDefaultCollector("CS")).persist();

        doImportForAtomicTestings(exportData).andExpect(status().is2xxSuccessful());
        clearEntityManager();

        for (Payload expected : payloadComposer.generatedItems) {
          Optional<Payload> recreated =
              getImportedInjectsFromDb().stream()
                  .map(Inject::getInjectorContract)
                  .filter(Optional::isPresent)
                  .map(injectorContract -> injectorContract.get().getPayload())
                  .filter(Objects::nonNull)
                  .filter(c -> c.getName().equals(expected.getName()))
                  .findAny();

          Assertions.assertTrue(recreated.isPresent(), "Could not find expected payload");
          Assertions.assertEquals(expected.getName(), recreated.get().getName());
          Assertions.assertEquals(expected.getDescription(), recreated.get().getDescription());
          Assertions.assertEquals(expected.getStatus(), recreated.get().getStatus());
          Assertions.assertEquals(
              expected.getCleanupCommand(), recreated.get().getCleanupCommand());
          Assertions.assertEquals(
              expected.getCleanupExecutor(), recreated.get().getCleanupExecutor());
          Assertions.assertEquals(expected.getExecutionArch(), recreated.get().getExecutionArch());
          Assertions.assertEquals(expected.getType(), recreated.get().getType());
          Assertions.assertEquals(expected.getSource(), recreated.get().getSource());
          Assertions.assertEquals(expected.getExternalId(), recreated.get().getExternalId());

          Assertions.assertNotEquals(expected.getId(), recreated.get().getId());

          Assertions.assertEquals(
              expected.getDetectionRemediations().size(),
              recreated.get().getDetectionRemediations().size());

          if (!expected.getDetectionRemediations().isEmpty()) {
            Assertions.assertEquals(
                expected.getDetectionRemediations().get(0).getValues(),
                recreated.get().getDetectionRemediations().get(0).getValues());
          }
        }
      }

      @Test
      @DisplayName("All platform teams have been recreated")
      public void allPlatformTeamsHaveBeenRecreated() throws Exception {
        byte[] exportData =
            getExportDataThenDelete(getInjectFromScenarioWrappers(), true, true, true);

        doImportForAtomicTestings(exportData).andExpect(status().is2xxSuccessful());
        clearEntityManager();

        for (Team expected :
            teamComposer.generatedItems.stream().filter(team -> !team.getContextual()).toList()) {
          Optional<Team> recreated =
              getImportedInjectsFromDb().stream()
                  .flatMap(inject -> inject.getTeams().stream())
                  .filter(c -> c.getName().equals(expected.getName()))
                  .findAny();

          Assertions.assertTrue(recreated.isPresent(), "Could not find expected team");
          Assertions.assertEquals(expected.getName(), recreated.get().getName());
          Assertions.assertEquals(expected.getDescription(), recreated.get().getDescription());
          Assertions.assertEquals(expected.getContextual(), recreated.get().getContextual());

          Assertions.assertNotEquals(expected.getId(), recreated.get().getId());
        }
      }

      @Test
      @DisplayName("Contextual teams have not been recreated")
      public void contextualTeamsAreNotRecreated() throws Exception {
        byte[] exportData =
            getExportDataThenDelete(getInjectFromScenarioWrappers(), true, true, true);

        doImportForAtomicTestings(exportData).andExpect(status().is2xxSuccessful());
        clearEntityManager();

        for (Team expected :
            teamComposer.generatedItems.stream().filter(Team::getContextual).toList()) {
          Optional<Team> recreated =
              getImportedInjectsFromDb().stream()
                  .flatMap(inject -> inject.getTeams().stream())
                  .filter(c -> c.getName().equals(expected.getName()))
                  .findAny();

          Assertions.assertTrue(recreated.isEmpty(), "Found unexpected contextual team");
        }
      }

      @Test
      @DisplayName("All users from platform teams have been recreated")
      public void allUsersFromPlatformTeamsHaveBeenRecreated() throws Exception {
        byte[] exportData =
            getExportDataThenDelete(getInjectFromScenarioWrappers(), true, true, true);

        doImportForAtomicTestings(exportData).andExpect(status().is2xxSuccessful());
        clearEntityManager();

        for (User expected :
            userComposer.generatedItems.stream()
                .filter(user -> user.getTeams().stream().anyMatch(Team::getContextual))
                .toList()) {
          Optional<User> recreated =
              getImportedInjectsFromDb().stream()
                  .flatMap(inject -> inject.getTeams().stream())
                  .flatMap(team -> team.getUsers().stream())
                  .filter(c -> c.getEmail().equals(expected.getEmail()))
                  .findAny();

          Assertions.assertTrue(recreated.isPresent(), "Could not find expected user");
          Assertions.assertEquals(expected.getName(), recreated.get().getName());
          Assertions.assertEquals(expected.getFirstname(), recreated.get().getFirstname());
          Assertions.assertEquals(expected.getLastname(), recreated.get().getLastname());
          Assertions.assertEquals(expected.getLang(), recreated.get().getLang());
          Assertions.assertEquals(expected.getEmail(), recreated.get().getEmail());
          Assertions.assertEquals(expected.getPhone(), recreated.get().getPhone());
          Assertions.assertEquals(expected.getPgpKey(), recreated.get().getPgpKey());
          Assertions.assertEquals(expected.getCountry(), recreated.get().getCountry());
          Assertions.assertEquals(expected.getCity(), recreated.get().getCity());

          Assertions.assertNotEquals(expected.getId(), recreated.get().getId());
        }
      }

      @Test
      @DisplayName("All organisations have been recreated")
      public void allOrganisationsHaveBeenRecreated() throws Exception {
        byte[] exportData =
            getExportDataThenDelete(getInjectFromScenarioWrappers(), true, true, true);

        doImportForAtomicTestings(exportData).andExpect(status().is2xxSuccessful());
        clearEntityManager();

        for (Organization expected : organizationComposer.generatedItems) {
          List<Organization> orgs = new ArrayList<>();
          orgs.addAll(
              getImportedInjectsFromDb().stream()
                  .flatMap(inject -> inject.getTeams().stream())
                  .flatMap(team -> team.getUsers().stream())
                  .map(User::getOrganization)
                  .filter(Objects::nonNull)
                  .toList());
          orgs.addAll(
              getImportedInjectsFromDb().stream()
                  .flatMap(inject -> inject.getTeams().stream())
                  .map(Team::getOrganization)
                  .filter(Objects::nonNull)
                  .toList());
          Optional<Organization> recreated =
              orgs.stream().filter(c -> c.getName().equals(expected.getName())).findAny();

          Assertions.assertTrue(recreated.isPresent(), "Could not find expected user");
          Assertions.assertEquals(expected.getName(), recreated.get().getName());
          Assertions.assertEquals(expected.getDescription(), recreated.get().getDescription());

          Assertions.assertNotEquals(expected.getId(), recreated.get().getId());
        }
      }

      @Test
      @DisplayName("All tags have been recreated")
      public void allTagsHaveBeenRecreated() throws Exception {
        byte[] exportData =
            getExportDataThenDelete(getInjectFromScenarioWrappers(), true, true, true);

        doImportForAtomicTestings(exportData).andExpect(status().is2xxSuccessful());
        clearEntityManager();

        for (Tag expected : tagComposer.generatedItems) {
          Optional<Tag> recreated =
              TagHelper.crawlAllInjectsTags(getImportedInjectsFromDb(), challengeService).stream()
                  .filter(c -> c.getName().equals(expected.getName()))
                  .findAny();

          Assertions.assertTrue(
              recreated.isPresent(),
              "Could not find expected tag '%s'".formatted(expected.getName()));
          Assertions.assertEquals(expected.getName(), recreated.get().getName());
          Assertions.assertEquals(expected.getColor(), recreated.get().getColor());

          Assertions.assertNotEquals(expected.getId(), recreated.get().getId());
        }
      }

      @Test
      @DisplayName("All documents have been recreated")
      public void allDocumentsHaveBeenRecreated() throws Exception {
        byte[] exportData =
            getExportDataThenDelete(getInjectFromScenarioWrappers(), true, true, true);

        doImportForAtomicTestings(exportData).andExpect(status().is2xxSuccessful());
        clearEntityManager();

        for (Document expected : documentComposer.generatedItems) {
          Optional<Document> recreated =
              crawlDocumentsFromInjects(getImportedInjectsFromDb()).stream()
                  .filter(c -> c.getName().equals(expected.getName()))
                  .findAny();

          Assertions.assertTrue(
              recreated.isPresent(),
              "Could not find expected document %s".formatted(expected.getTarget()));
          Assertions.assertEquals(expected.getName(), recreated.get().getName());
          Assertions.assertEquals(expected.getDescription(), recreated.get().getDescription());
          Assertions.assertEquals(expected.getTarget(), recreated.get().getTarget());
          Assertions.assertEquals(expected.getType(), recreated.get().getType());

          Assertions.assertNotEquals(expected.getId(), recreated.get().getId());
        }
      }
    }
  }

  @Nested
  @WithMockUser(withCapabilities = {Capability.ACCESS_ASSESSMENT, Capability.MANAGE_ASSESSMENT})
  @DisplayName("When imported objects already exist on the destination")
  public class WhenImportedObjectsAlreadyExistOnDestination {

    @Nested
    @DisplayName("When targeting an exercise")
    public class WhenTargetingAnExercise {

      private ExerciseComposer.Composer getPersistedExerciseWrapper() {
        return exerciseComposer.forExercise(ExerciseFixture.createDefaultExercise()).persist();
      }

      @Test
      @DisplayName("All injects were appended to exercise")
      public void allInjectsWereAppendedToExercise() throws Exception {
        byte[] exportData = getExportData(getInjectFromExerciseWrappers(), true, true, true);
        ExerciseComposer.Composer destinationExerciseWrapper = getPersistedExerciseWrapper();
        doImportForSimulation(destinationExerciseWrapper.get().getId(), exportData)
            .andExpect(status().is2xxSuccessful());

        for (Inject expected : injectComposer.generatedItems) {
          Exercise dest =
              exerciseRepository.findById(destinationExerciseWrapper.get().getId()).orElseThrow();
          Optional<Inject> reused =
              dest.getInjects().stream()
                  .filter(i -> i.getTitle().equals(expected.getTitle()))
                  .findAny();

          Assertions.assertTrue(reused.isPresent(), "Could not find expected inject");
          Assertions.assertEquals(expected.getCity(), reused.get().getCity());
          Assertions.assertEquals(expected.getCountry(), reused.get().getCountry());
          Assertions.assertEquals(expected.getDependsDuration(), reused.get().getDependsDuration());
          Assertions.assertEquals(
              expected.getNumberOfTargetUsers(), reused.get().getNumberOfTargetUsers());
          Assertions.assertEquals(expected.getDescription(), reused.get().getDescription());

          // the challenge ID is necessarily different from source and imported values, therefore
          // ignore
          // this
          assertThatJson(reused.get().getContent())
              .whenIgnoringPaths("challenges", "articles")
              .isEqualTo(expected.getContent());

          Assertions.assertNotEquals(expected.getId(), reused.get().getId());
        }
      }

      @Test
      @DisplayName("Create new articles anyway")
      public void createNewArticlesAnyway() throws Exception {
        byte[] exportData = getExportData(getInjectFromExerciseWrappers(), true, true, true);
        ExerciseComposer.Composer destinationExerciseWrapper = getPersistedExerciseWrapper();
        doImportForSimulation(destinationExerciseWrapper.get().getId(), exportData)
            .andExpect(status().is2xxSuccessful());
        clearEntityManager();

        for (Article expected : articleComposer.generatedItems) {
          Exercise dest =
              exerciseRepository.findById(destinationExerciseWrapper.get().getId()).orElseThrow();
          Optional<Article> reused =
              dest.getArticles().stream()
                  .filter(a -> a.getName().equals(expected.getName()))
                  .findFirst();

          Assertions.assertTrue(reused.isPresent(), "Could not find expected article");
          Assertions.assertEquals(expected.getName(), reused.get().getName());
          Assertions.assertEquals(expected.getAuthor(), reused.get().getAuthor());
          Assertions.assertEquals(expected.getComments(), reused.get().getComments());
          Assertions.assertEquals(expected.getLikes(), reused.get().getLikes());
          Assertions.assertEquals(expected.getContent(), reused.get().getContent());
          Assertions.assertEquals(expected.getShares(), reused.get().getShares());
          Assertions.assertEquals(
              expected.getVirtualPublication(), reused.get().getVirtualPublication());

          Assertions.assertNotEquals(expected.getId(), reused.get().getId());
        }
      }

      @Test
      @DisplayName("Existing channels are reused")
      public void existingChannelsAreReused() throws Exception {
        byte[] exportData = getExportData(getInjectFromExerciseWrappers(), true, true, true);
        ExerciseComposer.Composer destinationExerciseWrapper = getPersistedExerciseWrapper();
        doImportForSimulation(destinationExerciseWrapper.get().getId(), exportData)
            .andExpect(status().is2xxSuccessful());
        clearEntityManager();

        for (Channel expected : channelComposer.generatedItems) {
          Exercise dest =
              exerciseRepository.findById(destinationExerciseWrapper.get().getId()).orElseThrow();
          Optional<Channel> reused =
              dest.getArticles().stream()
                  .map(Article::getChannel)
                  .filter(c -> c.getId().equals(expected.getId()))
                  .findAny();

          Assertions.assertTrue(reused.isPresent(), "Could not find expected channel");
        }
      }

      @Test
      @DisplayName("Existing challenges are reused")
      public void existingChallengesAreReused() throws Exception {
        byte[] exportData = getExportData(getInjectFromExerciseWrappers(), true, true, true);
        ExerciseComposer.Composer destinationExerciseWrapper = getPersistedExerciseWrapper();
        doImportForSimulation(destinationExerciseWrapper.get().getId(), exportData)
            .andExpect(status().is2xxSuccessful());
        clearEntityManager();

        for (Challenge expected : challengeComposer.generatedItems) {
          Exercise dest =
              exerciseRepository.findById(destinationExerciseWrapper.get().getId()).orElseThrow();
          Optional<Challenge> reused =
              fromIterable(challengeService.getExerciseChallenges(dest.getId())).stream()
                  .filter(c -> c.getId().equals(expected.getId()))
                  .findAny();

          Assertions.assertTrue(reused.isPresent(), "Could not find expected challenge");
        }
      }

      @Test
      @DisplayName("Existing payloads are reused")
      public void existingPayloadsAreReused() throws Exception {
        byte[] exportData = getExportData(getInjectFromExerciseWrappers(), true, true, true);
        ExerciseComposer.Composer destinationExerciseWrapper = getPersistedExerciseWrapper();
        doImportForSimulation(destinationExerciseWrapper.get().getId(), exportData)
            .andExpect(status().is2xxSuccessful());
        clearEntityManager();

        for (Payload expected : payloadComposer.generatedItems) {
          Exercise dest =
              exerciseRepository.findById(destinationExerciseWrapper.get().getId()).orElseThrow();
          Optional<Payload> reused =
              dest.getInjects().stream()
                  .map(Inject::getInjectorContract)
                  .filter(Optional::isPresent)
                  .map(injectorContract -> injectorContract.get().getPayload())
                  .filter(Objects::nonNull)
                  .filter(c -> c.getId().equals(expected.getId()))
                  .findAny();

          Assertions.assertTrue(reused.isPresent(), "Could not find expected payload");
        }
      }

      @Test
      @DisplayName("Existing platform teams are assigned to exercise")
      public void existingPlatformTeamsAreAssignedToExercise() throws Exception {
        byte[] exportData = getExportData(getInjectFromExerciseWrappers(), true, true, true);
        ExerciseComposer.Composer destinationExerciseWrapper = getPersistedExerciseWrapper();
        doImportForSimulation(destinationExerciseWrapper.get().getId(), exportData)
            .andExpect(status().is2xxSuccessful());
        clearEntityManager();

        for (Team expected :
            teamComposer.generatedItems.stream().filter(team -> !team.getContextual()).toList()) {
          Exercise dest =
              exerciseRepository.findById(destinationExerciseWrapper.get().getId()).orElseThrow();
          Optional<Team> reused =
              dest.getTeams().stream().filter(c -> c.getId().equals(expected.getId())).findAny();

          Assertions.assertTrue(reused.isPresent(), "Could not find expected team");
        }
      }

      @Test
      @DisplayName("Contextual teams are recreated for exercise")
      public void contextualTeamsAreRecreatedForExercise() throws Exception {
        byte[] exportData = getExportData(getInjectFromScenarioWrappers(), true, true, true);
        ExerciseComposer.Composer destinationExerciseWrapper = getPersistedExerciseWrapper();
        doImportForSimulation(destinationExerciseWrapper.get().getId(), exportData)
            .andExpect(status().is2xxSuccessful());
        clearEntityManager();

        for (Team expected :
            teamComposer.generatedItems.stream().filter(Team::getContextual).toList()) {
          Exercise dest =
              exerciseRepository.findById(destinationExerciseWrapper.get().getId()).orElseThrow();
          Optional<Team> recreated =
              dest.getTeams().stream()
                  .filter(c -> c.getName().equals(expected.getName()))
                  .findAny();

          Assertions.assertTrue(recreated.isPresent(), "Could not find expected team");
          Assertions.assertEquals(expected.getName(), recreated.get().getName());
          Assertions.assertEquals(expected.getDescription(), recreated.get().getDescription());
          Assertions.assertEquals(expected.getContextual(), recreated.get().getContextual());

          Assertions.assertNotEquals(expected.getId(), recreated.get().getId());
        }
      }

      @Test
      @DisplayName("Existing users are assigned to exercise")
      public void existingUsersAreAssignedToExercise() throws Exception {
        byte[] exportData = getExportData(getInjectFromExerciseWrappers(), true, true, true);
        ExerciseComposer.Composer destinationExerciseWrapper = getPersistedExerciseWrapper();
        doImportForSimulation(destinationExerciseWrapper.get().getId(), exportData)
            .andExpect(status().is2xxSuccessful());
        clearEntityManager();

        for (User expected : userComposer.generatedItems) {
          Exercise dest =
              exerciseRepository.findById(destinationExerciseWrapper.get().getId()).orElseThrow();
          Optional<User> reused =
              dest.getTeams().stream()
                  .flatMap(team -> team.getUsers().stream())
                  .filter(c -> c.getId().equals(expected.getId()))
                  .findAny();

          Assertions.assertTrue(reused.isPresent(), "Could not find expected user");
        }
      }

      @Test
      @DisplayName("Existing organisations are assigned to exercise")
      public void existingOrganisationsAreAssignedToExercise() throws Exception {
        byte[] exportData = getExportData(getInjectFromExerciseWrappers(), true, true, true);
        ExerciseComposer.Composer destinationExerciseWrapper = getPersistedExerciseWrapper();
        doImportForSimulation(destinationExerciseWrapper.get().getId(), exportData)
            .andExpect(status().is2xxSuccessful());
        clearEntityManager();

        for (Organization expected : organizationComposer.generatedItems) {
          Exercise dest =
              exerciseRepository.findById(destinationExerciseWrapper.get().getId()).orElseThrow();
          List<Organization> orgs = new ArrayList<>();
          orgs.addAll(
              dest.getInjects().stream()
                  .flatMap(inject -> inject.getTeams().stream())
                  .flatMap(team -> team.getUsers().stream())
                  .map(User::getOrganization)
                  .filter(Objects::nonNull)
                  .toList());
          orgs.addAll(
              dest.getInjects().stream()
                  .flatMap(inject -> inject.getTeams().stream())
                  .map(Team::getOrganization)
                  .filter(Objects::nonNull)
                  .toList());
          Optional<Organization> reused =
              orgs.stream().filter(c -> c.getId().equals(expected.getId())).findAny();

          Assertions.assertTrue(reused.isPresent(), "Could not find expected organisation");
        }
      }

      @Test
      @DisplayName("Existing tags are reused")
      public void existingTagsAreReused() throws Exception {
        byte[] exportData = getExportData(getInjectFromExerciseWrappers(), true, true, true);
        ExerciseComposer.Composer destinationExerciseWrapper = getPersistedExerciseWrapper();
        doImportForSimulation(destinationExerciseWrapper.get().getId(), exportData)
            .andExpect(status().is2xxSuccessful());
        clearEntityManager();

        for (Tag expected : tagComposer.generatedItems) {
          Exercise dest =
              exerciseRepository.findById(destinationExerciseWrapper.get().getId()).orElseThrow();

          Optional<Tag> reused =
              TagHelper.crawlAllExerciseTags(dest, challengeService).stream()
                  .filter(c -> c.getId().equals(expected.getId()))
                  .findAny();

          Assertions.assertTrue(
              reused.isPresent(), "Could not find expected tag '%s'".formatted(expected.getName()));
        }
      }

      @Test
      @DisplayName("Existing documents are reused")
      public void existingDocumentsAreReused() throws Exception {
        byte[] exportData = getExportData(getInjectFromExerciseWrappers(), true, true, true);
        ExerciseComposer.Composer destinationExerciseWrapper = getPersistedExerciseWrapper();
        doImportForSimulation(destinationExerciseWrapper.get().getId(), exportData)
            .andExpect(status().is2xxSuccessful());
        clearEntityManager();

        for (Document expected : documentComposer.generatedItems) {
          Exercise dest =
              exerciseRepository.findById(destinationExerciseWrapper.get().getId()).orElseThrow();
          Optional<Document> reused =
              crawlDocumentsFromInjects(dest.getInjects()).stream()
                  .filter(c -> c.getId().equals(expected.getId()))
                  .findAny();

          Assertions.assertTrue(
              reused.isPresent(),
              "Could not find expected document %s".formatted(expected.getTarget()));
        }
      }
    }

    @Nested
    @DisplayName("When targeting a scenario")
    public class WhenTargetingAScenario {

      private ScenarioComposer.Composer getPersistedScenarioWrapper() {
        return scenarioComposer
            .forScenario(ScenarioFixture.createDefaultIncidentResponseScenario())
            .persist();
      }

      @Test
      @DisplayName("All injects were appended to scenario")
      public void allInjectsWereAppendedToScenario() throws Exception {
        byte[] exportData = getExportData(getInjectFromScenarioWrappers(), true, true, true);
        ScenarioComposer.Composer destinationScenarioWrapper = getPersistedScenarioWrapper();
        doImportForScenario(destinationScenarioWrapper.get().getId(), exportData)
            .andExpect(status().is2xxSuccessful());

        for (Inject expected : injectComposer.generatedItems) {
          Scenario dest =
              scenarioRepository.findById(destinationScenarioWrapper.get().getId()).orElseThrow();
          Optional<Inject> reused =
              dest.getInjects().stream()
                  .filter(i -> i.getTitle().equals(expected.getTitle()))
                  .findAny();

          Assertions.assertTrue(reused.isPresent(), "Could not find expected inject");
          Assertions.assertEquals(expected.getCity(), reused.get().getCity());
          Assertions.assertEquals(expected.getCountry(), reused.get().getCountry());
          Assertions.assertEquals(expected.getDependsDuration(), reused.get().getDependsDuration());
          Assertions.assertEquals(
              expected.getNumberOfTargetUsers(), reused.get().getNumberOfTargetUsers());
          Assertions.assertEquals(expected.getDescription(), reused.get().getDescription());

          // the challenge ID is necessarily different from source and imported values, therefore
          // ignore
          // this
          assertThatJson(reused.get().getContent())
              .whenIgnoringPaths("challenges", "articles")
              .isEqualTo(expected.getContent());

          Assertions.assertNotEquals(expected.getId(), reused.get().getId());
        }
      }

      @Test
      @DisplayName("Create new articles anyway")
      public void createNewArticlesAnyway() throws Exception {
        byte[] exportData = getExportData(getInjectFromScenarioWrappers(), true, true, true);
        ScenarioComposer.Composer destinationScenarioWrapper = getPersistedScenarioWrapper();
        doImportForScenario(destinationScenarioWrapper.get().getId(), exportData)
            .andExpect(status().is2xxSuccessful());
        clearEntityManager();

        for (Article expected : articleComposer.generatedItems) {
          Scenario dest =
              scenarioRepository.findById(destinationScenarioWrapper.get().getId()).orElseThrow();
          Optional<Article> reused =
              dest.getArticles().stream()
                  .filter(a -> a.getName().equals(expected.getName()))
                  .findFirst();

          Assertions.assertTrue(reused.isPresent(), "Could not find expected article");
          Assertions.assertEquals(expected.getName(), reused.get().getName());
          Assertions.assertEquals(expected.getAuthor(), reused.get().getAuthor());
          Assertions.assertEquals(expected.getComments(), reused.get().getComments());
          Assertions.assertEquals(expected.getLikes(), reused.get().getLikes());
          Assertions.assertEquals(expected.getContent(), reused.get().getContent());
          Assertions.assertEquals(expected.getShares(), reused.get().getShares());
          Assertions.assertEquals(
              expected.getVirtualPublication(), reused.get().getVirtualPublication());

          Assertions.assertNotEquals(expected.getId(), reused.get().getId());
        }
      }

      @Test
      @DisplayName("Existing channels are reused")
      public void existingChannelsAreReused() throws Exception {
        byte[] exportData = getExportData(getInjectFromScenarioWrappers(), true, true, true);
        ScenarioComposer.Composer destinationScenarioWrapper = getPersistedScenarioWrapper();
        doImportForScenario(destinationScenarioWrapper.get().getId(), exportData)
            .andExpect(status().is2xxSuccessful());
        clearEntityManager();

        for (Channel expected : channelComposer.generatedItems) {
          Scenario dest =
              scenarioRepository.findById(destinationScenarioWrapper.get().getId()).orElseThrow();
          Optional<Channel> reused =
              dest.getArticles().stream()
                  .map(Article::getChannel)
                  .filter(c -> c.getId().equals(expected.getId()))
                  .findAny();

          Assertions.assertTrue(reused.isPresent(), "Could not find expected channel");
        }
      }

      @Test
      @DisplayName("Existing challenges are reused")
      public void existingChallengesAreReused() throws Exception {
        byte[] exportData = getExportData(getInjectFromScenarioWrappers(), true, true, true);
        ScenarioComposer.Composer destinationScenarioWrapper = getPersistedScenarioWrapper();
        doImportForScenario(destinationScenarioWrapper.get().getId(), exportData)
            .andExpect(status().is2xxSuccessful());
        clearEntityManager();

        for (Challenge expected : challengeComposer.generatedItems) {
          Scenario dest =
              scenarioRepository.findById(destinationScenarioWrapper.get().getId()).orElseThrow();
          Optional<Challenge> reused =
              fromIterable(challengeService.getScenarioChallenges(dest)).stream()
                  .filter(c -> c.getId().equals(expected.getId()))
                  .findAny();

          Assertions.assertTrue(reused.isPresent(), "Could not find expected challenge");
        }
      }

      @Test
      @DisplayName("Existing payloads are reused")
      public void existingPayloadsAreReused() throws Exception {
        byte[] exportData = getExportData(getInjectFromScenarioWrappers(), true, true, true);
        ScenarioComposer.Composer destinationScenarioWrapper = getPersistedScenarioWrapper();
        doImportForScenario(destinationScenarioWrapper.get().getId(), exportData)
            .andExpect(status().is2xxSuccessful());
        clearEntityManager();

        for (Payload expected : payloadComposer.generatedItems) {
          Scenario dest =
              scenarioRepository.findById(destinationScenarioWrapper.get().getId()).orElseThrow();
          Optional<Payload> reused =
              dest.getInjects().stream()
                  .map(Inject::getInjectorContract)
                  .filter(Optional::isPresent)
                  .map(injectorContract -> injectorContract.get().getPayload())
                  .filter(Objects::nonNull)
                  .filter(c -> c.getId().equals(expected.getId()))
                  .findAny();

          Assertions.assertTrue(reused.isPresent(), "Could not find expected payload");
        }
      }

      @Test
      @DisplayName("Existing platform teams are assigned to scenario")
      public void existingPlatformTeamsAreAssignedToScenario() throws Exception {
        byte[] exportData = getExportData(getInjectFromScenarioWrappers(), true, true, true);
        ScenarioComposer.Composer destinationScenarioWrapper = getPersistedScenarioWrapper();
        doImportForScenario(destinationScenarioWrapper.get().getId(), exportData)
            .andExpect(status().is2xxSuccessful());
        clearEntityManager();

        for (Team expected :
            teamComposer.generatedItems.stream().filter(team -> !team.getContextual()).toList()) {
          Scenario dest =
              scenarioRepository.findById(destinationScenarioWrapper.get().getId()).orElseThrow();
          Optional<Team> reused =
              dest.getTeams().stream().filter(c -> c.getId().equals(expected.getId())).findAny();

          Assertions.assertTrue(reused.isPresent(), "Could not find expected team");
        }
      }

      @Test
      @DisplayName("Contextual teams are recreated for scenario")
      public void contextualTeamsAreRecreatedForScenario() throws Exception {
        byte[] exportData = getExportData(getInjectFromScenarioWrappers(), true, true, true);
        ScenarioComposer.Composer destinationScenarioWrapper = getPersistedScenarioWrapper();
        doImportForScenario(destinationScenarioWrapper.get().getId(), exportData)
            .andExpect(status().is2xxSuccessful());
        clearEntityManager();

        for (Team expected :
            teamComposer.generatedItems.stream().filter(Team::getContextual).toList()) {
          Scenario dest =
              scenarioRepository.findById(destinationScenarioWrapper.get().getId()).orElseThrow();
          Optional<Team> recreated =
              dest.getTeams().stream()
                  .filter(c -> c.getName().equals(expected.getName()))
                  .findAny();

          Assertions.assertTrue(recreated.isPresent(), "Could not find expected team");
          Assertions.assertEquals(expected.getName(), recreated.get().getName());
          Assertions.assertEquals(expected.getDescription(), recreated.get().getDescription());
          Assertions.assertEquals(expected.getContextual(), recreated.get().getContextual());

          Assertions.assertNotEquals(expected.getId(), recreated.get().getId());
        }
      }

      @Test
      @DisplayName("Existing users are assigned to scenario")
      public void existingUsersAreAssignedToScenario() throws Exception {
        byte[] exportData = getExportData(getInjectFromScenarioWrappers(), true, true, true);
        ScenarioComposer.Composer destinationScenarioWrapper = getPersistedScenarioWrapper();
        doImportForScenario(destinationScenarioWrapper.get().getId(), exportData)
            .andExpect(status().is2xxSuccessful());
        clearEntityManager();

        for (User expected : userComposer.generatedItems) {
          Scenario dest =
              scenarioRepository.findById(destinationScenarioWrapper.get().getId()).orElseThrow();
          Optional<User> reused =
              dest.getTeams().stream()
                  .flatMap(team -> team.getUsers().stream())
                  .filter(c -> c.getId().equals(expected.getId()))
                  .findAny();

          Assertions.assertTrue(reused.isPresent(), "Could not find expected user");
        }
      }

      @Test
      @DisplayName("Existing organisations are assigned to scenario")
      public void existingOrganisationsAreAssignedToScenario() throws Exception {
        byte[] exportData = getExportData(getInjectFromScenarioWrappers(), true, true, true);
        ScenarioComposer.Composer destinationScenarioWrapper = getPersistedScenarioWrapper();
        doImportForScenario(destinationScenarioWrapper.get().getId(), exportData)
            .andExpect(status().is2xxSuccessful());
        clearEntityManager();

        for (Organization expected : organizationComposer.generatedItems) {
          Scenario dest =
              scenarioRepository.findById(destinationScenarioWrapper.get().getId()).orElseThrow();
          List<Organization> orgs = new ArrayList<>();
          orgs.addAll(
              dest.getInjects().stream()
                  .flatMap(inject -> inject.getTeams().stream())
                  .flatMap(team -> team.getUsers().stream())
                  .map(User::getOrganization)
                  .filter(Objects::nonNull)
                  .toList());
          orgs.addAll(
              dest.getInjects().stream()
                  .flatMap(inject -> inject.getTeams().stream())
                  .map(Team::getOrganization)
                  .filter(Objects::nonNull)
                  .toList());
          Optional<Organization> reused =
              orgs.stream().filter(c -> c.getId().equals(expected.getId())).findAny();

          Assertions.assertTrue(reused.isPresent(), "Could not find expected organisation");
        }
      }

      @Test
      @DisplayName("Existing tags are reused")
      public void existingTagsAreReused() throws Exception {
        byte[] exportData = getExportData(getInjectFromScenarioWrappers(), true, true, true);
        ScenarioComposer.Composer destinationScenarioWrapper = getPersistedScenarioWrapper();
        doImportForScenario(destinationScenarioWrapper.get().getId(), exportData)
            .andExpect(status().is2xxSuccessful());
        clearEntityManager();

        for (Tag expected : tagComposer.generatedItems) {
          Scenario dest =
              scenarioRepository.findById(destinationScenarioWrapper.get().getId()).orElseThrow();

          Optional<Tag> reused =
              TagHelper.crawlAllScenarioTags(dest, challengeService).stream()
                  .filter(c -> c.getId().equals(expected.getId()))
                  .findAny();

          Assertions.assertTrue(
              reused.isPresent(), "Could not find expected tag '%s'".formatted(expected.getName()));
        }
      }

      @Test
      @DisplayName("Existing documents are reused")
      public void existingDocumentsAreReused() throws Exception {
        byte[] exportData = getExportData(getInjectFromScenarioWrappers(), true, true, true);
        ScenarioComposer.Composer destinationScenarioWrapper = getPersistedScenarioWrapper();
        doImportForScenario(destinationScenarioWrapper.get().getId(), exportData)
            .andExpect(status().is2xxSuccessful());
        clearEntityManager();

        for (Document expected : documentComposer.generatedItems) {
          Scenario dest =
              scenarioRepository.findById(destinationScenarioWrapper.get().getId()).orElseThrow();
          Optional<Document> reused =
              crawlDocumentsFromInjects(dest.getInjects()).stream()
                  .filter(c -> c.getId().equals(expected.getId()))
                  .findAny();

          Assertions.assertTrue(
              reused.isPresent(),
              "Could not find expected document %s".formatted(expected.getTarget()));
        }
      }
    }

    @Nested
    @WithMockUser(isAdmin = true)
    @DisplayName("When targeting atomic testing")
    public class WhenTargetingAtomicTesting {

      @Test
      @DisplayName("Each inject is added to its own atomic testing")
      public void eachInjectWasAddedToAtomicTesting() throws Exception {
        byte[] exportData = getExportData(getInjectFromScenarioWrappers(), true, true, true);

        doImportForAtomicTestings(exportData).andExpect(status().is2xxSuccessful());

        for (Inject expected : injectComposer.generatedItems) {
          Optional<Inject> reused =
              getImportedInjectsFromDb().stream()
                  .filter(i -> i.getTitle().equals(expected.getTitle()))
                  .findAny();

          Assertions.assertTrue(reused.isPresent(), "Could not find expected inject");
          Assertions.assertEquals(expected.getCity(), reused.get().getCity());
          Assertions.assertEquals(expected.getCountry(), reused.get().getCountry());
          Assertions.assertEquals(expected.getDependsDuration(), reused.get().getDependsDuration());
          Assertions.assertEquals(
              expected.getNumberOfTargetUsers(), reused.get().getNumberOfTargetUsers());
          Assertions.assertEquals(expected.getDescription(), reused.get().getDescription());

          // the challenge ID is necessarily different from source and imported values, therefore
          // ignore
          // this
          assertThatJson(reused.get().getContent())
              .whenIgnoringPaths("challenges", "articles")
              .isEqualTo(expected.getContent());

          Assertions.assertNotEquals(expected.getId(), reused.get().getId());
        }
      }

      @Test
      @DisplayName("Create new articles anyway")
      public void createNewArticlesAnyway() throws Exception {
        byte[] exportData = getExportData(getInjectFromScenarioWrappers(), true, true, true);
        doImportForAtomicTestings(exportData).andExpect(status().is2xxSuccessful());
        clearEntityManager();

        for (Article expected : articleComposer.generatedItems) {
          Optional<Article> reused =
              articleService.getInjectsArticles(getImportedInjectsFromDb()).stream()
                  .filter(a -> a.getName().equals(expected.getName()))
                  .findFirst();

          Assertions.assertTrue(reused.isPresent(), "Could not find expected article");
          Assertions.assertEquals(expected.getName(), reused.get().getName());
          Assertions.assertEquals(expected.getAuthor(), reused.get().getAuthor());
          Assertions.assertEquals(expected.getComments(), reused.get().getComments());
          Assertions.assertEquals(expected.getLikes(), reused.get().getLikes());
          Assertions.assertEquals(expected.getContent(), reused.get().getContent());
          Assertions.assertEquals(expected.getShares(), reused.get().getShares());
          Assertions.assertEquals(
              expected.getVirtualPublication(), reused.get().getVirtualPublication());

          Assertions.assertNotEquals(expected.getId(), reused.get().getId());
        }
      }

      @Test
      @DisplayName("Existing channels are reused")
      public void existingChannelsAreReused() throws Exception {
        byte[] exportData = getExportData(getInjectFromScenarioWrappers(), true, true, true);
        doImportForAtomicTestings(exportData).andExpect(status().is2xxSuccessful());
        clearEntityManager();

        for (Channel expected : channelComposer.generatedItems) {
          Optional<Channel> reused =
              articleService.getInjectsArticles(getImportedInjectsFromDb()).stream()
                  .map(Article::getChannel)
                  .filter(c -> c.getId().equals(expected.getId()))
                  .findAny();

          Assertions.assertTrue(reused.isPresent(), "Could not find expected channel");
        }
      }

      @Test
      @DisplayName("Existing challenges are reused")
      public void existingChallengesAreReused() throws Exception {
        byte[] exportData = getExportData(getInjectFromScenarioWrappers(), true, true, true);
        doImportForAtomicTestings(exportData).andExpect(status().is2xxSuccessful());
        clearEntityManager();

        for (Challenge expected : challengeComposer.generatedItems) {
          Optional<Challenge> reused =
              fromIterable(challengeService.getInjectsChallenges(getImportedInjectsFromDb()))
                  .stream()
                  .filter(c -> c.getId().equals(expected.getId()))
                  .findAny();

          Assertions.assertTrue(reused.isPresent(), "Could not find expected challenge");
        }
      }

      @Test
      @DisplayName("Existing payloads are reused")
      public void existingPayloadsAreReused() throws Exception {
        byte[] exportData = getExportData(getInjectFromScenarioWrappers(), true, true, true);
        doImportForAtomicTestings(exportData).andExpect(status().is2xxSuccessful());
        clearEntityManager();

        for (Payload expected : payloadComposer.generatedItems) {
          Optional<Payload> reused =
              getImportedInjectsFromDb().stream()
                  .map(Inject::getInjectorContract)
                  .filter(Optional::isPresent)
                  .map(injectorContract -> injectorContract.get().getPayload())
                  .filter(Objects::nonNull)
                  .filter(c -> c.getId().equals(expected.getId()))
                  .findAny();

          Assertions.assertTrue(reused.isPresent(), "Could not find expected payload");
        }
      }

      @Test
      @DisplayName("Existing platform teams are assigned to atomic testing")
      public void existingPlatformTeamsAreAssignedToAtomicTesting() throws Exception {
        byte[] exportData = getExportData(getInjectFromScenarioWrappers(), true, true, true);
        doImportForAtomicTestings(exportData).andExpect(status().is2xxSuccessful());
        clearEntityManager();

        for (Team expected :
            teamComposer.generatedItems.stream().filter(team -> !team.getContextual()).toList()) {
          Optional<Team> reused =
              getImportedInjectsFromDb().stream()
                  .flatMap(inject -> inject.getTeams().stream())
                  .filter(c -> c.getId().equals(expected.getId()))
                  .findAny();

          Assertions.assertTrue(reused.isPresent(), "Could not find expected team");
        }
      }

      @Test
      @DisplayName("Contextual teams are not recreated for atomic testing")
      public void contextualTeamsAreNotRecreatedForAtomicTesting() throws Exception {
        byte[] exportData = getExportData(getInjectFromScenarioWrappers(), true, true, true);
        doImportForAtomicTestings(exportData).andExpect(status().is2xxSuccessful());
        clearEntityManager();

        for (Team expected :
            teamComposer.generatedItems.stream().filter(Team::getContextual).toList()) {
          Optional<Team> recreated =
              getImportedInjectsFromDb().stream()
                  .flatMap(inject -> inject.getTeams().stream())
                  .filter(c -> c.getName().equals(expected.getName()))
                  .findAny();

          Assertions.assertTrue(recreated.isEmpty(), "Found unexpected contextual team");
        }
      }

      @Test
      @DisplayName("Existing users are assigned to atomic testing")
      public void existingUsersAreAssignedToAtomicTesting() throws Exception {
        byte[] exportData = getExportData(getInjectFromScenarioWrappers(), true, true, true);
        doImportForAtomicTestings(exportData).andExpect(status().is2xxSuccessful());
        clearEntityManager();

        for (User expected :
            userComposer.generatedItems.stream()
                .filter(user -> user.getTeams().stream().anyMatch(Team::getContextual))
                .toList()) {
          Optional<User> reused =
              getImportedInjectsFromDb().stream()
                  .flatMap(inject -> inject.getTeams().stream())
                  .flatMap(team -> team.getUsers().stream())
                  .filter(c -> c.getId().equals(expected.getId()))
                  .findAny();

          Assertions.assertTrue(reused.isPresent(), "Could not find expected user");
        }
      }

      @Test
      @DisplayName("Existing organisations are assigned to atomic testing")
      public void existingOrganisationsAreAssignedToAtomicTesting() throws Exception {
        byte[] exportData = getExportData(getInjectFromScenarioWrappers(), true, true, true);
        doImportForAtomicTestings(exportData).andExpect(status().is2xxSuccessful());
        clearEntityManager();

        for (Organization expected : organizationComposer.generatedItems) {
          List<Organization> orgs = new ArrayList<>();
          orgs.addAll(
              getImportedInjectsFromDb().stream()
                  .flatMap(inject -> inject.getTeams().stream())
                  .flatMap(team -> team.getUsers().stream())
                  .map(User::getOrganization)
                  .filter(Objects::nonNull)
                  .toList());
          orgs.addAll(
              getImportedInjectsFromDb().stream()
                  .flatMap(inject -> inject.getTeams().stream())
                  .map(Team::getOrganization)
                  .filter(Objects::nonNull)
                  .toList());
          Optional<Organization> reused =
              orgs.stream().filter(c -> c.getId().equals(expected.getId())).findAny();

          Assertions.assertTrue(reused.isPresent(), "Could not find expected organisation");
        }
      }

      @Test
      @DisplayName("Existing tags are reused")
      public void existingTagsAreReused() throws Exception {
        byte[] exportData = getExportData(getInjectFromScenarioWrappers(), true, true, true);
        doImportForAtomicTestings(exportData).andExpect(status().is2xxSuccessful());
        clearEntityManager();

        for (Tag expected : tagComposer.generatedItems) {

          Optional<Tag> reused =
              TagHelper.crawlAllInjectsTags(getImportedInjectsFromDb(), challengeService).stream()
                  .filter(c -> c.getId().equals(expected.getId()))
                  .findAny();

          Assertions.assertTrue(
              reused.isPresent(), "Could not find expected tag '%s'".formatted(expected.getName()));
        }
      }

      @Test
      @DisplayName("Existing documents are reused")
      public void existingDocumentsAreReused() throws Exception {
        byte[] exportData = getExportData(getInjectFromScenarioWrappers(), true, true, true);
        doImportForAtomicTestings(exportData).andExpect(status().is2xxSuccessful());
        clearEntityManager();

        for (Document expected : documentComposer.generatedItems) {
          Optional<Document> reused =
              crawlDocumentsFromInjects(getImportedInjectsFromDb()).stream()
                  .filter(c -> c.getId().equals(expected.getId()))
                  .findAny();

          Assertions.assertTrue(
              reused.isPresent(),
              "Could not find expected document %s".formatted(expected.getTarget()));
        }
      }
    }
  }
}
