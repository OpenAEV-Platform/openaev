package io.openaev.rest.exercise.imports;

import static io.openaev.rest.exercise.ExerciseApi.EXERCISE_URI;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.database.model.Tag;
import io.openaev.database.repository.*;
import io.openaev.rest.exercise.exports.ExportOptions;
import io.openaev.rest.exercise.service.ExportService;
import io.openaev.service.ChallengeService;
import io.openaev.utils.constants.Constants;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.composers.*;
import io.openaev.utils.helpers.TagHelper;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@TestInstance(PER_CLASS)
public class ExerciseApiImportWithoutExistingItemsTest extends IntegrationTest {
  @Autowired private MockMvc mvc;
  @Autowired private VariableComposer variableComposer;
  @Autowired private VariableRepository variableRepository;
  @Autowired private TeamComposer teamComposer;
  @Autowired private TeamRepository teamRepository;
  @Autowired private UserComposer userComposer;
  @Autowired private UserRepository userRepository;
  @Autowired private OrganizationComposer organizationComposer;
  @Autowired private OrganizationRepository organizationRepository;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectRepository injectRepository;
  @Autowired private ChallengeComposer challengeComposer;
  @Autowired private ChallengeRepository challengeRepository;
  @Autowired private ObjectiveComposer objectiveComposer;
  @Autowired private ObjectiveRepository objectiveRepository;
  @Autowired private DocumentComposer documentComposer;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private TagComposer tagComposer;
  @Autowired private TagRepository tagRepository;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private ArticleComposer articleComposer;
  @Autowired private ArticleRepository articleRepository;
  @Autowired private ChannelComposer channelComposer;
  @Autowired private ChannelRepository channelRepository;
  @Autowired private LessonsQuestionsComposer lessonsQuestionsComposer;
  @Autowired private LessonsQuestionRepository lessonsQuestionRepository;
  @Autowired private LessonsCategoryComposer lessonsCategoryComposer;
  @Autowired private LessonsCategoryRepository lessonsCategoryRepository;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private ExportService exportService;
  @Autowired private EntityManager entityManager;
  @Autowired private ChallengeService challengeService;

  private static final int DEFAULT_EXPORT_OPTIONS = ExportOptions.mask(false, false, false);
  private static final int FULL_EXPORT_OPTIONS = ExportOptions.mask(true, true, true);

  @BeforeEach
  void before() {
    lessonsQuestionsComposer.reset();
    lessonsCategoryComposer.reset();
    teamComposer.reset();
    userComposer.reset();
    variableComposer.reset();
    organizationComposer.reset();
    injectComposer.reset();
    challengeComposer.reset();
    channelComposer.reset();
    articleComposer.reset();
    objectiveComposer.reset();
    documentComposer.reset();
    tagComposer.reset();
    exerciseComposer.reset();
  }

  // this is part of the "Arrange" part of the AAA pattern for the following tests
  // it runs out most tests use this exact structure as test data, therefore it's in its own
  // function up here
  private ExerciseComposer.Composer getExercise() {
    return exerciseComposer
        .forExercise(ExerciseFixture.createDefaultExercise())
        .withArticle(
            articleComposer
                .forArticle(ArticleFixture.getDefaultArticle())
                .withChannel(channelComposer.forChannel(ChannelFixture.getDefaultChannel())))
        .withLessonCategory(
            lessonsCategoryComposer
                .forLessonsCategory(LessonsCategoryFixture.createDefaultLessonsCategory())
                .withLessonsQuestion(
                    lessonsQuestionsComposer.forLessonsQuestion(
                        LessonsQuestionFixture.createDefaultLessonsQuestion())))
        .withTeam(
            teamComposer
                .forTeam(TeamFixture.getDefaultTeam())
                .withOrganisation(
                    organizationComposer.forOrganization(
                        OrganizationFixture.createDefaultOrganisation()))
                .withTag(tagComposer.forTag(TagFixture.getTagWithText("Team tag")))
                .withUser(userComposer.forUser(UserFixture.getUserWithDefaultEmail()))
                .withUser(
                    userComposer
                        .forUser(UserFixture.getUserWithDefaultEmail())
                        .withTag(tagComposer.forTag(TagFixture.getTagWithText("User tag")))
                        .withOrganization(
                            organizationComposer
                                .forOrganization(OrganizationFixture.createDefaultOrganisation())
                                .withTag(
                                    tagComposer.forTag(
                                        TagFixture.getTagWithText("Organization tag"))))))
        .withTeamUsers()
        .withInject(
            injectComposer
                .forInject(InjectFixture.getDefaultInject())
                .withTag(tagComposer.forTag(TagFixture.getTagWithText("Inject tag")))
                .withInjectorContract(
                    injectorContractComposer
                        .forInjectorContract(
                            InjectorContractFixture.createDefaultInjectorContract())
                        .withChallenge(
                            challengeComposer
                                .forChallenge(ChallengeFixture.createDefaultChallenge())
                                .withTag(
                                    tagComposer.forTag(
                                        TagFixture.getTagWithText("Challenge tag"))))))
        .withDocument(
            documentComposer
                .forDocument(DocumentFixture.getDocument(FileFixture.getPlainTextFileContent()))
                .withTag(tagComposer.forTag(TagFixture.getTagWithText("Document tag")))
                .withInMemoryFile(FileFixture.getPlainTextFileContent()))
        .withObjective(objectiveComposer.forObjective(ObjectiveFixture.getDefaultObjective()))
        .withTag(tagComposer.forTag(TagFixture.getTagWithText("Exercise tag")))
        .withVariable(variableComposer.forVariable(VariableFixture.getDefaultVariable()));
  }

  private Exercise findImportedExerciseFromDb(String baseName) {
    String importedName = "%s%s".formatted(baseName, Constants.IMPORTED_OBJECT_NAME_SUFFIX);
    Optional<Exercise> exerciseOpt =
        exerciseRepository.findAll().stream()
            .filter(ex -> ex.getName().equals(importedName))
            .findFirst();
    if (exerciseOpt.isEmpty()) {
      Assertions.fail("Imported exercise of name %s found".formatted(importedName));
    }
    return exerciseOpt.get();
  }

  private byte[] doExport(ExerciseComposer.Composer composer) throws Exception {
    Exercise exercise = composer.persist().get();
    return exportService.exportExerciseToZip(exercise, FULL_EXPORT_OPTIONS);
  }

  @DisplayName(
      "Given a valid export zip file, given no preexisting objects, exercise imported correctly")
  @Test
  @WithMockUser(isAdmin = true)
  public void
      given_a_valid_export_zip_file_given_no_preexisting_objects_exercise_imported_correctly()
          throws Exception {
    ExerciseComposer.Composer exerciseWrapper = getExercise();
    byte[] zipBytes = doExport(exerciseWrapper);
    exerciseWrapper.delete();

    MockMultipartFile mmf = new MockMultipartFile("file", zipBytes);

    mvc.perform(
            multipart(EXERCISE_URI + "/import")
                .file(mmf)
                .contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().is2xxSuccessful());

    // force hibernate to clear its cache to not pollute fetch operations
    // TODO: make this automatic somehow, perhaps within Composers
    entityManager.flush();
    entityManager.clear();

    Exercise imported = findImportedExerciseFromDb(exerciseWrapper.get().getName());
    Exercise expected = exerciseWrapper.get();

    Assertions.assertEquals(
        "%s%s".formatted(expected.getName(), Constants.IMPORTED_OBJECT_NAME_SUFFIX),
        imported.getName());
    Assertions.assertEquals(expected.getDescription(), imported.getDescription());
    Assertions.assertEquals(expected.getStatus(), imported.getStatus());
    Assertions.assertEquals(expected.getSubtitle(), imported.getSubtitle());
    Assertions.assertEquals(expected.getHeader(), imported.getHeader());
    Assertions.assertEquals(expected.getFooter(), imported.getFooter());
    Assertions.assertEquals(expected.getFrom(), imported.getFrom());

    Assertions.assertNotEquals(expected.getId(), imported.getId());
  }

  @DisplayName("Given a valid export zip file, given no preexisting objects, create new teams")
  @Test
  @WithMockUser(isAdmin = true)
  public void given_a_valid_export_zip_file_given_no_preexisting_objects_create_new_teams()
      throws Exception {
    ExerciseComposer.Composer exerciseWrapper = getExercise();
    byte[] zipBytes = doExport(exerciseWrapper);
    exerciseWrapper.delete();

    MockMultipartFile mmf = new MockMultipartFile("file", zipBytes);

    mvc.perform(
            multipart(EXERCISE_URI + "/import")
                .file(mmf)
                .contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().is2xxSuccessful());

    // force hibernate to clear its cache to not pollute fetch operations
    // TODO: make this automatic somehow, perhaps within Composers
    entityManager.flush();
    entityManager.clear();

    for (Team expected : teamComposer.generatedItems) {
      Optional<Team> teamFromDb =
          teamRepository.findAllByNameIgnoreCase(expected.getName()).stream().findFirst();
      if (teamFromDb.isEmpty()) {
        Assertions.fail("Team " + expected.getName() + " not found");
      }
      Team imported = teamFromDb.get();

      Assertions.assertEquals(expected.getName(), imported.getName());
      Assertions.assertEquals(expected.getDescription(), imported.getDescription());

      Assertions.assertNotEquals(expected.getId(), imported.getId());
    }
  }

  @DisplayName(
      "Given a valid export zip file, given no preexisting objects, new teams attached to imported exercise")
  @Test
  @WithMockUser(isAdmin = true)
  public void
      given_a_valid_export_zip_file_given_no_preexisting_objects_new_teams_attached_to_imported_exercise()
          throws Exception {
    ExerciseComposer.Composer exerciseWrapper = getExercise();
    byte[] zipBytes = doExport(exerciseWrapper);
    exerciseWrapper.delete();

    MockMultipartFile mmf = new MockMultipartFile("file", zipBytes);

    mvc.perform(
            multipart(EXERCISE_URI + "/import")
                .file(mmf)
                .contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().is2xxSuccessful());

    // force hibernate to clear its cache to not pollute fetch operations
    // TODO: make this automatic somehow, perhaps within Composers
    entityManager.flush();
    entityManager.clear();

    List<Team> dbTeams = findImportedExerciseFromDb(exerciseWrapper.get().getName()).getTeams();
    for (Team expected : exerciseWrapper.get().getTeams()) {
      Assertions.assertTrue(
          dbTeams.stream().anyMatch(team -> team.getName().equals(expected.getName())),
          "Team %s not found in imported exercise".formatted(expected.getName()));
    }
  }

  @DisplayName("Given a valid export zip file, given no preexisting objects, create new users")
  @Test
  @WithMockUser(isAdmin = true)
  public void given_a_valid_export_zip_file_given_no_preexisting_objects_create_new_users()
      throws Exception {
    ExerciseComposer.Composer exerciseWrapper = getExercise();
    byte[] zipBytes = doExport(exerciseWrapper);
    exerciseWrapper.delete();

    MockMultipartFile mmf = new MockMultipartFile("file", zipBytes);

    mvc.perform(
            multipart(EXERCISE_URI + "/import")
                .file(mmf)
                .contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().is2xxSuccessful());

    // force hibernate to clear its cache to not pollute fetch operations
    // TODO: make this automatic somehow, perhaps within Composers
    entityManager.flush();
    entityManager.clear();

    for (User expected : userComposer.generatedItems) {
      Optional<User> userFromDb =
          userRepository.findByEmailIgnoreCase(expected.getEmail()).stream().findFirst();
      if (userFromDb.isEmpty()) {
        Assertions.fail("User with email %s not found".formatted(expected.getEmail()));
      }
      User imported = userFromDb.get();

      Assertions.assertEquals(expected.getName(), imported.getName());
      Assertions.assertEquals(expected.getFirstname(), imported.getFirstname());
      Assertions.assertEquals(expected.getLastname(), imported.getLastname());
      Assertions.assertEquals(expected.getLang(), imported.getLang());
      Assertions.assertEquals(expected.getEmail(), imported.getEmail());
      Assertions.assertEquals(expected.getPhone(), imported.getPhone());
      Assertions.assertEquals(expected.getPgpKey(), imported.getPgpKey());
      Assertions.assertEquals(expected.getCountry(), imported.getCountry());
      Assertions.assertEquals(expected.getCity(), imported.getCity());

      Assertions.assertNotEquals(expected.getId(), imported.getId());
    }
  }

  @DisplayName(
      "Given a valid export zip file, given no preexisting objects, new users attached to imported exercise")
  @Test
  @WithMockUser(isAdmin = true)
  public void
      given_a_valid_export_zip_file_given_no_preexisting_objects_new_users_attached_to_imported_exercise()
          throws Exception {
    ExerciseComposer.Composer exerciseWrapper = getExercise();
    byte[] zipBytes = doExport(exerciseWrapper);
    exerciseWrapper.delete();

    MockMultipartFile mmf = new MockMultipartFile("file", zipBytes);

    mvc.perform(
            multipart(EXERCISE_URI + "/import")
                .file(mmf)
                .contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().is2xxSuccessful());

    // force hibernate to clear its cache to not pollute fetch operations
    // TODO: make this automatic somehow, perhaps within Composers
    entityManager.flush();
    entityManager.clear();

    List<User> dbUsers =
        findImportedExerciseFromDb(exerciseWrapper.get().getName()).getTeams().stream()
            .flatMap(team -> team.getUsers().stream())
            .toList();
    for (User expected : userComposer.generatedItems) {
      Assertions.assertTrue(
          dbUsers.stream()
              .anyMatch(userFromDb -> userFromDb.getEmail().equals(expected.getEmail())));
    }
  }

  @DisplayName(
      "Given a valid export zip file, given no preexisting objects, create new organisations")
  @Test
  @WithMockUser(isAdmin = true)
  public void given_a_valid_export_zip_file_given_no_preexisting_objects_create_new_organisations()
      throws Exception {
    ExerciseComposer.Composer exerciseWrapper = getExercise();
    byte[] zipBytes = doExport(exerciseWrapper);
    exerciseWrapper.delete();

    MockMultipartFile mmf = new MockMultipartFile("file", zipBytes);

    mvc.perform(
            multipart(EXERCISE_URI + "/import")
                .file(mmf)
                .contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().is2xxSuccessful());

    // force hibernate to clear its cache to not pollute fetch operations
    // TODO: make this automatic somehow, perhaps within Composers
    entityManager.flush();
    entityManager.clear();

    for (Organization expected : organizationComposer.generatedItems) {
      Optional<Organization> orgFromDb =
          organizationRepository.findByNameIgnoreCase(expected.getName()).stream().findFirst();
      if (orgFromDb.isEmpty()) {
        Assertions.fail("Organization " + expected.getName() + " not found");
      }
      Organization imported = orgFromDb.get();

      Assertions.assertEquals(expected.getName(), imported.getName());
      Assertions.assertEquals(expected.getDescription(), imported.getDescription());

      Assertions.assertNotEquals(expected.getId(), imported.getId());
    }
  }

  @DisplayName(
      "Given a valid export zip file, given no preexisting objects, new organisations attached to imported exercise")
  @Test
  @WithMockUser(isAdmin = true)
  public void
      given_a_valid_export_zip_file_given_no_preexisting_objects_new_organisations_attached_to_imported_exercise()
          throws Exception {
    ExerciseComposer.Composer exerciseWrapper = getExercise();
    byte[] zipBytes = doExport(exerciseWrapper);
    exerciseWrapper.delete();

    MockMultipartFile mmf = new MockMultipartFile("file", zipBytes);

    mvc.perform(
            multipart(EXERCISE_URI + "/import")
                .file(mmf)
                .contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().is2xxSuccessful());

    // force hibernate to clear its cache to not pollute fetch operations
    // TODO: make this automatic somehow, perhaps within Composers
    entityManager.flush();
    entityManager.clear();

    List<Organization> dbOrgs =
        new java.util.ArrayList<>(
            findImportedExerciseFromDb(exerciseWrapper.get().getName()).getTeams().stream()
                .flatMap(team -> team.getUsers().stream().map(User::getOrganization))
                .filter(Objects::nonNull)
                .toList());
    dbOrgs.addAll(
        findImportedExerciseFromDb(exerciseWrapper.get().getName()).getTeams().stream()
            .map(Team::getOrganization)
            .toList());
    for (Organization expected : organizationComposer.generatedItems) {
      Assertions.assertTrue(
          dbOrgs.stream().anyMatch(o -> o.getName().equals(expected.getName())),
          "Expected organization " + expected.getName() + " not found");
    }
  }

  @DisplayName("Given a valid export zip file, given no preexisting objects, create new articles")
  @Test
  @WithMockUser(isAdmin = true)
  public void given_a_valid_export_zip_file_given_no_preexisting_objects_create_new_articles()
      throws Exception {
    ExerciseComposer.Composer exerciseWrapper = getExercise();
    byte[] zipBytes = doExport(exerciseWrapper);
    exerciseWrapper.delete();

    MockMultipartFile mmf = new MockMultipartFile("file", zipBytes);

    mvc.perform(
            multipart(EXERCISE_URI + "/import")
                .file(mmf)
                .contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().is2xxSuccessful());

    // force hibernate to clear its cache to not pollute fetch operations
    // TODO: make this automatic somehow, perhaps within Composers
    entityManager.flush();
    entityManager.clear();

    for (Article expected : articleComposer.generatedItems) {
      Optional<Article> articleFromDb =
          articleRepository
              .findAll(
                  (root, query, criteriaBuilder) ->
                      criteriaBuilder.equal(root.get("name"), expected.getName()))
              .stream()
              .findFirst();
      if (articleFromDb.isEmpty()) {
        Assertions.fail("Article " + expected.getName() + " not found");
      }
      Article imported = articleFromDb.get();

      Assertions.assertEquals(expected.getName(), imported.getName());
      Assertions.assertEquals(expected.getContent(), imported.getContent());
      Assertions.assertEquals(expected.getShares(), imported.getShares());
      Assertions.assertEquals(expected.getAuthor(), imported.getAuthor());
      Assertions.assertEquals(expected.getLikes(), imported.getLikes());
      Assertions.assertEquals(expected.getComments(), imported.getComments());

      Assertions.assertNotEquals(expected.getId(), imported.getId());
    }
  }

  @DisplayName(
      "Given a valid export zip file, given no preexisting objects, new articles attached to imported exercise")
  @Test
  @WithMockUser(isAdmin = true)
  public void
      given_a_valid_export_zip_file_given_no_preexisting_objects_new_articles_attached_to_imported_exercise()
          throws Exception {
    ExerciseComposer.Composer exerciseWrapper = getExercise();
    byte[] zipBytes = doExport(exerciseWrapper);
    exerciseWrapper.delete();

    MockMultipartFile mmf = new MockMultipartFile("file", zipBytes);

    mvc.perform(
            multipart(EXERCISE_URI + "/import")
                .file(mmf)
                .contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().is2xxSuccessful());

    // force hibernate to clear its cache to not pollute fetch operations
    // TODO: make this automatic somehow, perhaps within Composers
    entityManager.flush();
    entityManager.clear();

    List<Article> dbArticles =
        findImportedExerciseFromDb(exerciseWrapper.get().getName()).getArticles();
    for (Article expected : articleComposer.generatedItems) {
      Assertions.assertTrue(
          dbArticles.stream().anyMatch(art -> art.getName().equals(expected.getName())),
          "Article " + expected.getName() + " not found in imported exercise");
    }
  }

  @DisplayName("Given a valid export zip file, given no preexisting objects, create new channels")
  @Test
  @WithMockUser(isAdmin = true)
  public void given_a_valid_export_zip_file_given_no_preexisting_objects_create_new_channels()
      throws Exception {
    ExerciseComposer.Composer exerciseWrapper = getExercise();
    byte[] zipBytes = doExport(exerciseWrapper);
    exerciseWrapper.delete();

    MockMultipartFile mmf = new MockMultipartFile("file", zipBytes);

    mvc.perform(
            multipart(EXERCISE_URI + "/import")
                .file(mmf)
                .contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().is2xxSuccessful());

    // force hibernate to clear its cache to not pollute fetch operations
    // TODO: make this automatic somehow, perhaps within Composers
    entityManager.flush();
    entityManager.clear();

    Channel expected = channelComposer.generatedItems.getFirst();
    Optional<Channel> channelFromDb =
        channelRepository
            .findAll(
                (root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("name"), expected.getName()))
            .stream()
            .findFirst();
    if (channelFromDb.isEmpty()) {
      Assertions.fail();
    }
    Channel imported = channelFromDb.get();

    Assertions.assertEquals(expected.getName(), imported.getName());
    Assertions.assertEquals(expected.getType(), imported.getType());
    Assertions.assertEquals(expected.getDescription(), imported.getDescription());
    Assertions.assertEquals(expected.getMode(), imported.getMode());

    Assertions.assertNotEquals(expected.getId(), imported.getId());
  }

  @DisplayName(
      "Given a valid export zip file, given no preexisting objects, new channels attached to imported exercise")
  @Test
  @WithMockUser(isAdmin = true)
  public void
      given_a_valid_export_zip_file_given_no_preexisting_objects_new_channels_attached_to_imported_exercise()
          throws Exception {
    ExerciseComposer.Composer exerciseWrapper = getExercise();
    byte[] zipBytes = doExport(exerciseWrapper);
    exerciseWrapper.delete();

    MockMultipartFile mmf = new MockMultipartFile("file", zipBytes);

    mvc.perform(
            multipart(EXERCISE_URI + "/import")
                .file(mmf)
                .contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().is2xxSuccessful());

    // force hibernate to clear its cache to not pollute fetch operations
    // TODO: make this automatic somehow, perhaps within Composers
    entityManager.flush();
    entityManager.clear();

    List<Channel> dbChannels =
        findImportedExerciseFromDb(exerciseWrapper.get().getName()).getArticles().stream()
            .map(Article::getChannel)
            .toList();
    for (Channel expected : channelComposer.generatedItems) {
      Assertions.assertTrue(
          dbChannels.stream().anyMatch(channel -> channel.getName().equals(expected.getName())),
          "Channel " + expected.getName() + " not found in imported exercise");
    }
  }

  @DisplayName("Given a valid export zip file, given no preexisting objects, create new tags")
  @Test
  @WithMockUser(isAdmin = true)
  public void given_a_valid_export_zip_file_given_no_preexisting_objects_create_new_tags()
      throws Exception {
    ExerciseComposer.Composer exerciseWrapper = getExercise();
    byte[] zipBytes = doExport(exerciseWrapper);
    exerciseWrapper.delete();

    MockMultipartFile mmf = new MockMultipartFile("file", zipBytes);

    mvc.perform(
            multipart(EXERCISE_URI + "/import")
                .file(mmf)
                .contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().is2xxSuccessful());

    // force hibernate to clear its cache to not pollute fetch operations
    // TODO: make this automatic somehow, perhaps within Composers
    entityManager.flush();
    entityManager.clear();

    for (Tag expected : tagComposer.generatedItems) {
      Optional<Tag> tagFromDb = tagRepository.findByName(expected.getName());
      if (tagFromDb.isEmpty()) {
        Assertions.fail("Tag " + expected.getName() + " not found");
      }
      Tag imported = tagFromDb.get();

      Assertions.assertEquals(expected.getName(), imported.getName());
      Assertions.assertEquals(expected.getColor(), imported.getColor());

      Assertions.assertNotEquals(expected.getId(), imported.getId());
    }
  }

  @DisplayName(
      "Given a valid export zip file, given no preexisting objects, new tags attached to imported exercise")
  @Test
  @WithMockUser(isAdmin = true)
  public void
      given_a_valid_export_zip_file_given_no_preexisting_objects_new_tags_attached_to_imported_exercise()
          throws Exception {
    ExerciseComposer.Composer exerciseWrapper = getExercise();
    byte[] zipBytes = doExport(exerciseWrapper);
    exerciseWrapper.delete();

    MockMultipartFile mmf = new MockMultipartFile("file", zipBytes);

    mvc.perform(
            multipart(EXERCISE_URI + "/import")
                .file(mmf)
                .contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().is2xxSuccessful());

    // force hibernate to clear its cache to not pollute fetch operations
    // TODO: make this automatic somehow, perhaps within Composers
    entityManager.flush();
    entityManager.clear();

    List<Tag> dbTags =
        TagHelper.crawlAllExerciseTags(
            findImportedExerciseFromDb(exerciseWrapper.get().getName()), challengeService);
    for (Tag expected : tagComposer.generatedItems) {
      Assertions.assertTrue(
          dbTags.stream().anyMatch(tag -> tag.getName().equals(expected.getName())),
          "Tag " + expected.getName() + " not found in imported exercise");
    }
  }

  @DisplayName("Given a valid export zip file, given no preexisting objects, create new objectives")
  @Test
  @WithMockUser(isAdmin = true)
  public void given_a_valid_export_zip_file_given_no_preexisting_objects_create_new_objectives()
      throws Exception {
    ExerciseComposer.Composer exerciseWrapper = getExercise();
    byte[] zipBytes = doExport(exerciseWrapper);
    exerciseWrapper.delete();

    MockMultipartFile mmf = new MockMultipartFile("file", zipBytes);

    mvc.perform(
            multipart(EXERCISE_URI + "/import")
                .file(mmf)
                .contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().is2xxSuccessful());

    // force hibernate to clear its cache to not pollute fetch operations
    // TODO: make this automatic somehow, perhaps within Composers
    entityManager.flush();
    entityManager.clear();

    for (Objective expected : objectiveComposer.generatedItems) {
      Optional<Objective> objectiveFromDb =
          StreamSupport.stream(objectiveRepository.findAll().spliterator(), false)
              .filter(objective -> objective.getTitle().equals(expected.getTitle()))
              .findFirst();
      if (objectiveFromDb.isEmpty()) {
        Assertions.fail("Objective " + expected.getTitle() + " not found");
      }
      Objective imported = objectiveFromDb.get();

      Assertions.assertEquals(expected.getTitle(), imported.getTitle());
      Assertions.assertEquals(expected.getDescription(), imported.getDescription());

      Assertions.assertNotEquals(expected.getId(), imported.getId());
    }
  }

  @DisplayName(
      "Given a valid export zip file, given no preexisting objects, new objectives attached to imported exercise")
  @Test
  @WithMockUser(isAdmin = true)
  public void
      given_a_valid_export_zip_file_given_no_preexisting_objects_new_objectives_attached_to_imported_exercise()
          throws Exception {
    ExerciseComposer.Composer exerciseWrapper = getExercise();
    byte[] zipBytes = doExport(exerciseWrapper);
    exerciseWrapper.delete();

    MockMultipartFile mmf = new MockMultipartFile("file", zipBytes);

    mvc.perform(
            multipart(EXERCISE_URI + "/import")
                .file(mmf)
                .contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().is2xxSuccessful());

    // force hibernate to clear its cache to not pollute fetch operations
    // TODO: make this automatic somehow, perhaps within Composers
    entityManager.flush();
    entityManager.clear();

    List<Objective> dbObjectives =
        findImportedExerciseFromDb(exerciseWrapper.get().getName()).getObjectives();
    for (Objective expected : objectiveComposer.generatedItems) {
      Assertions.assertTrue(
          dbObjectives.stream()
              .anyMatch(objective -> objective.getTitle().equals(expected.getTitle())),
          "Objective " + expected.getTitle() + " not found in imported exercise");
    }
  }

  @DisplayName(
      "Given a valid export zip file, given no preexisting objects, create new lessons categories")
  @Test
  @WithMockUser(isAdmin = true)
  public void
      given_a_valid_export_zip_file_given_no_preexisting_objects_create_new_lessons_categories()
          throws Exception {
    ExerciseComposer.Composer exerciseWrapper = getExercise();
    byte[] zipBytes = doExport(exerciseWrapper);
    exerciseWrapper.delete();

    MockMultipartFile mmf = new MockMultipartFile("file", zipBytes);

    mvc.perform(
            multipart(EXERCISE_URI + "/import")
                .file(mmf)
                .contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().is2xxSuccessful());

    // force hibernate to clear its cache to not pollute fetch operations
    // TODO: make this automatic somehow, perhaps within Composers
    entityManager.flush();
    entityManager.clear();

    for (LessonsCategory expected : lessonsCategoryComposer.generatedItems) {
      Optional<LessonsCategory> categoryFromDb =
          StreamSupport.stream(lessonsCategoryRepository.findAll().spliterator(), false)
              .filter(category -> category.getName().equals(expected.getName()))
              .findFirst();
      if (categoryFromDb.isEmpty()) {
        Assertions.fail("Lessons Category " + expected.getName() + " not found");
      }
      LessonsCategory imported = categoryFromDb.get();

      Assertions.assertEquals(expected.getName(), imported.getName());
      Assertions.assertEquals(expected.getDescription(), imported.getDescription());
      Assertions.assertEquals(expected.getOrder(), imported.getOrder());

      Assertions.assertNotEquals(expected.getId(), imported.getId());
    }
  }

  @DisplayName(
      "Given a valid export zip file, given no preexisting objects, new lessons categories attached to imported exercise")
  @Test
  @WithMockUser(isAdmin = true)
  public void
      given_a_valid_export_zip_file_given_no_preexisting_objects_new_lessons_categories_attached_to_imported_exercise()
          throws Exception {
    ExerciseComposer.Composer exerciseWrapper = getExercise();
    byte[] zipBytes = doExport(exerciseWrapper);
    exerciseWrapper.delete();

    MockMultipartFile mmf = new MockMultipartFile("file", zipBytes);

    mvc.perform(
            multipart(EXERCISE_URI + "/import")
                .file(mmf)
                .contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().is2xxSuccessful());

    // force hibernate to clear its cache to not pollute fetch operations
    // TODO: make this automatic somehow, perhaps within Composers
    entityManager.flush();
    entityManager.clear();

    List<LessonsCategory> dbCategories =
        findImportedExerciseFromDb(exerciseWrapper.get().getName()).getLessonsCategories();
    for (LessonsCategory expected : lessonsCategoryComposer.generatedItems) {
      Assertions.assertTrue(
          dbCategories.stream().anyMatch(category -> category.getName().equals(expected.getName())),
          "Lessons Category " + expected.getName() + " not found in imported exercise");
    }
  }

  @DisplayName("Given a valid export zip file, given no preexisting objects, create new documents")
  @Test
  @WithMockUser(isAdmin = true)
  public void given_a_valid_export_zip_file_given_no_preexisting_objects_create_new_documents()
      throws Exception {
    ExerciseComposer.Composer exerciseWrapper = getExercise();
    byte[] zipBytes = doExport(exerciseWrapper);
    exerciseWrapper.delete();

    MockMultipartFile mmf = new MockMultipartFile("file", zipBytes);

    mvc.perform(
            multipart(EXERCISE_URI + "/import")
                .file(mmf)
                .contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().is2xxSuccessful());

    // force hibernate to clear its cache to not pollute fetch operations
    // TODO: make this automatic somehow, perhaps within Composers
    entityManager.flush();
    entityManager.clear();

    for (Document expected : documentComposer.generatedItems) {
      Optional<Document> docFromDb = documentRepository.findByName(expected.getName());
      if (docFromDb.isEmpty()) {
        Assertions.fail("Document " + expected.getName() + " not found");
      }
      Document imported = docFromDb.get();

      Assertions.assertEquals(expected.getName(), imported.getName());
      Assertions.assertEquals(expected.getDescription(), imported.getDescription());
      Assertions.assertEquals(expected.getTarget(), imported.getTarget());

      Assertions.assertNotEquals(expected.getId(), imported.getId());
    }
  }

  @DisplayName(
      "Given a valid export zip file, given no preexisting objects, new documents attached to imported exercise")
  @Test
  @WithMockUser(isAdmin = true)
  public void
      given_a_valid_export_zip_file_given_no_preexisting_objects_new_documents_attached_to_imported_exercise()
          throws Exception {
    ExerciseComposer.Composer exerciseWrapper = getExercise();
    byte[] zipBytes = doExport(exerciseWrapper);
    exerciseWrapper.delete();

    MockMultipartFile mmf = new MockMultipartFile("file", zipBytes);

    mvc.perform(
            multipart(EXERCISE_URI + "/import")
                .file(mmf)
                .contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().is2xxSuccessful());

    // force hibernate to clear its cache to not pollute fetch operations
    // TODO: make this automatic somehow, perhaps within Composers
    entityManager.flush();
    entityManager.clear();

    List<Document> dbDocs =
        findImportedExerciseFromDb(exerciseWrapper.get().getName()).getDocuments();
    for (Document expected : documentComposer.generatedItems) {
      Assertions.assertTrue(
          dbDocs.stream().anyMatch(doc -> doc.getName().equals(expected.getName())),
          "Document " + expected.getName() + " not found in imported exercise");
    }
  }

  @DisplayName("Given a valid export zip file, given no preexisting objects, create injects")
  @Test
  @WithMockUser(isAdmin = true)
  public void given_a_valid_export_zip_file_given_no_preexisting_objects_create_new_injects()
      throws Exception {
    ExerciseComposer.Composer exerciseWrapper = getExercise();
    byte[] zipBytes = doExport(exerciseWrapper);
    exerciseWrapper.delete();

    MockMultipartFile mmf = new MockMultipartFile("file", zipBytes);

    mvc.perform(
            multipart(EXERCISE_URI + "/import")
                .file(mmf)
                .contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().is2xxSuccessful());

    // force hibernate to clear its cache to not pollute fetch operations
    // TODO: make this automatic somehow, perhaps within Composers
    entityManager.flush();
    entityManager.clear();

    for (Inject expected : injectComposer.generatedItems) {
      Optional<Inject> injectFromDb =
          injectRepository
              .findAll(
                  (root, query, criteriaBuilder) ->
                      criteriaBuilder.equal(root.get("title"), expected.getTitle()))
              .stream()
              .findFirst();
      if (injectFromDb.isEmpty()) {
        Assertions.fail("Inject " + expected.getTitle() + " not found");
      }
      Inject imported = injectFromDb.get();

      Assertions.assertEquals(expected.getTitle(), imported.getTitle());
      Assertions.assertEquals(expected.getDescription(), imported.getDescription());
      // the challenge ID is necessarily different from source and imported values, therefore ignore
      // this
      assertThatJson(imported.getContent())
          .whenIgnoringPaths("challenges")
          .isEqualTo(expected.getContent());
      assertThatJson(imported.getContent()).node("challenges").isPresent().and().isArray();

      Assertions.assertNotEquals(expected.getId(), imported.getId());
    }
  }

  @DisplayName(
      "Given a valid export zip file, given no preexisting objects, new injects attached to imported exercise")
  @Test
  @WithMockUser(isAdmin = true)
  public void
      given_a_valid_export_zip_file_given_no_preexisting_objects_new_injects_attached_to_imported_exercise()
          throws Exception {
    ExerciseComposer.Composer exerciseWrapper = getExercise();
    byte[] zipBytes = doExport(exerciseWrapper);
    exerciseWrapper.delete();

    MockMultipartFile mmf = new MockMultipartFile("file", zipBytes);

    mvc.perform(
            multipart(EXERCISE_URI + "/import")
                .file(mmf)
                .contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().is2xxSuccessful());

    // force hibernate to clear its cache to not pollute fetch operations
    // TODO: make this automatic somehow, perhaps within Composers
    entityManager.flush();
    entityManager.clear();

    List<Inject> dbInjects =
        findImportedExerciseFromDb(exerciseWrapper.get().getName()).getInjects();
    for (Inject expected : injectComposer.generatedItems) {
      Assertions.assertTrue(
          dbInjects.stream().anyMatch(doc -> doc.getTitle().equals(expected.getTitle())),
          "Inject " + expected.getTitle() + " not found in imported exercise");
    }
  }

  @DisplayName("Given a valid export zip file, given no preexisting objects, create new variables")
  @Test
  @WithMockUser(isAdmin = true)
  public void given_a_valid_export_zip_file_given_no_preexisting_objects_create_new_variables()
      throws Exception {
    ExerciseComposer.Composer exerciseWrapper = getExercise();
    byte[] zipBytes = doExport(exerciseWrapper);
    exerciseWrapper.delete();

    MockMultipartFile mmf = new MockMultipartFile("file", zipBytes);

    mvc.perform(
            multipart(EXERCISE_URI + "/import")
                .file(mmf)
                .contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().is2xxSuccessful());

    // force hibernate to clear its cache to not pollute fetch operations
    // TODO: make this automatic somehow, perhaps within Composers
    entityManager.flush();
    entityManager.flush();

    for (Variable expected : variableComposer.generatedItems) {
      Optional<Variable> varFromDb =
          variableRepository
              .findAll(
                  (root, query, criteriaBuilder) ->
                      criteriaBuilder.equal(root.get("key"), expected.getKey()))
              .stream()
              .findFirst();
      if (varFromDb.isEmpty()) {
        Assertions.fail("Variable " + expected.getKey() + " not found");
      }
      Variable imported = varFromDb.get();

      Assertions.assertEquals(expected.getKey(), imported.getKey());
      Assertions.assertEquals(expected.getDescription(), imported.getDescription());
      Assertions.assertEquals(expected.getDescription(), imported.getDescription());

      Assertions.assertNotEquals(expected.getId(), imported.getId());
    }
  }

  @DisplayName(
      "Given a valid export zip file, given no preexisting objects, new variables attached to imported exercise")
  @Test
  @WithMockUser(isAdmin = true)
  public void
      given_a_valid_export_zip_file_given_no_preexisting_objects_new_variables_attached_to_imported_exercise()
          throws Exception {
    ExerciseComposer.Composer exerciseWrapper = getExercise();
    byte[] zipBytes = doExport(exerciseWrapper);
    exerciseWrapper.delete();

    MockMultipartFile mmf = new MockMultipartFile("file", zipBytes);

    mvc.perform(
            multipart(EXERCISE_URI + "/import")
                .file(mmf)
                .contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().is2xxSuccessful());

    // force hibernate to clear its cache to not pollute fetch operations
    // TODO: make this automatic somehow, perhaps within Composers
    entityManager.flush();
    entityManager.clear();

    List<Variable> dbVars =
        findImportedExerciseFromDb(exerciseWrapper.get().getName()).getVariables();
    for (Variable expected : variableComposer.generatedItems) {
      Assertions.assertTrue(
          dbVars.stream().anyMatch(var -> var.getKey().equals(expected.getKey())),
          "Variable " + expected.getKey() + " not found in imported exercise");
    }
  }

  @DisplayName("Given a valid export zip file, given no preexisting objects, create new challenges")
  @Test
  @WithMockUser(isAdmin = true)
  public void given_a_valid_export_zip_file_given_no_preexisting_objects_create_new_challenges()
      throws Exception {
    ExerciseComposer.Composer exerciseWrapper = getExercise();
    byte[] zipBytes = doExport(exerciseWrapper);
    exerciseWrapper.delete();

    MockMultipartFile mmf = new MockMultipartFile("file", zipBytes);

    mvc.perform(
            multipart(EXERCISE_URI + "/import")
                .file(mmf)
                .contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().is2xxSuccessful());

    // force hibernate to clear its cache to not pollute fetch operations
    // TODO: make this automatic somehow, perhaps within Composers
    entityManager.flush();
    entityManager.flush();

    for (Challenge expected : challengeComposer.generatedItems) {
      Optional<Challenge> challengeFromDb =
          challengeRepository.findByNameIgnoreCase(expected.getName()).stream().findFirst();
      if (challengeFromDb.isEmpty()) {
        Assertions.fail("Challenge " + expected.getName() + " not found");
      }
      Challenge imported = challengeFromDb.get();

      Assertions.assertEquals(expected.getName(), imported.getName());
      Assertions.assertEquals(expected.getContent(), imported.getContent());
      Assertions.assertEquals(expected.getCategory(), imported.getCategory());
      Assertions.assertEquals(expected.getScore(), imported.getScore());
      Assertions.assertEquals(expected.getMaxAttempts(), imported.getMaxAttempts());
      for (ChallengeFlag flag : expected.getFlags()) {
        Assertions.assertTrue(
            imported.getFlags().stream()
                .anyMatch(
                    flg ->
                        flg.getType().equals(flag.getType())
                            && flg.getValue().equals(flag.getValue())),
            "Flag of type " + flag.getType() + " not found in challenge");
      }

      Assertions.assertNotEquals(expected.getId(), imported.getId());
    }
  }

  @DisplayName(
      "Given a valid export zip file, given no preexisting objects, new challenges attached to imported exercise")
  @Test
  @WithMockUser(isAdmin = true)
  public void
      given_a_valid_export_zip_file_given_no_preexisting_objects_new_challenges_attached_to_imported_exercise()
          throws Exception {
    ExerciseComposer.Composer exerciseWrapper = getExercise();
    byte[] zipBytes = doExport(exerciseWrapper);
    exerciseWrapper.delete();

    MockMultipartFile mmf = new MockMultipartFile("file", zipBytes);

    mvc.perform(
            multipart(EXERCISE_URI + "/import")
                .file(mmf)
                .contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().is2xxSuccessful());

    // force hibernate to clear its cache to not pollute fetch operations
    // TODO: make this automatic somehow, perhaps within Composers
    entityManager.flush();
    entityManager.clear();

    List<Inject> dbInjects =
        findImportedExerciseFromDb(exerciseWrapper.get().getName()).getInjects();
    for (Challenge expected : challengeComposer.generatedItems) {
      Optional<Challenge> challengeFromDb =
          challengeRepository.findByNameIgnoreCase(expected.getName()).stream().findFirst();
      if (challengeFromDb.isEmpty()) {
        Assertions.fail("Challenge " + expected.getName() + " not found");
      }
      Challenge imported = challengeFromDb.get();

      Assertions.assertTrue(
          dbInjects.stream()
              .anyMatch(inject -> inject.getContent().toString().contains(imported.getId())),
          "Challenge " + expected.getName() + " not found in imported exercise");
    }
  }
}
