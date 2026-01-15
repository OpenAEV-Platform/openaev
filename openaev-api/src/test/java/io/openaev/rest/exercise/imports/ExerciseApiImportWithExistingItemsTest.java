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
public class ExerciseApiImportWithExistingItemsTest extends IntegrationTest {
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
      "Given a valid export zip file, given existing objects, assign existing teams to imported exercise")
  @Test
  @WithMockUser(isAdmin = true)
  public void
      given_a_valid_export_zip_file_given_existing_objects_assign_existing_teams_to_imported_exercise()
          throws Exception {
    ExerciseComposer.Composer exerciseWrapper = getExercise();
    byte[] zipBytes = doExport(exerciseWrapper);

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
          dbTeams.stream().anyMatch(team -> team.getId().equals(expected.getId())),
          "Team %s not found in imported exercise".formatted(expected.getName()));
    }
  }

  @DisplayName(
      "Given a valid export zip file, given existing objects, assign existing users to imported exercise")
  @Test
  @WithMockUser(isAdmin = true)
  public void
      given_a_valid_export_zip_file_given_existing_objects_assign_existing_users_to_imported_exercise()
          throws Exception {
    ExerciseComposer.Composer exerciseWrapper = getExercise();
    byte[] zipBytes = doExport(exerciseWrapper);

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
          dbUsers.stream().anyMatch(userFromDb -> userFromDb.getId().equals(expected.getId())));
    }
  }

  @DisplayName(
      "Given a valid export zip file, given existing objects, assign existing organisations to imported exercise")
  @Test
  @WithMockUser(isAdmin = true)
  public void
      given_a_valid_export_zip_file_given_existing_objects_assign_existing_organisations_to_imported_exercise()
          throws Exception {
    ExerciseComposer.Composer exerciseWrapper = getExercise();
    byte[] zipBytes = doExport(exerciseWrapper);

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
          dbOrgs.stream().anyMatch(o -> o.getId().equals(expected.getId())),
          "Expected organization " + expected.getName() + " not found");
    }
  }

  @DisplayName("Given a valid export zip file, given existing objects, create new article anyway")
  @Test
  @WithMockUser(isAdmin = true)
  public void given_a_valid_export_zip_file_given_existing_objects_create_new_article_anyway()
      throws Exception {
    ExerciseComposer.Composer exerciseWrapper = getExercise();
    byte[] zipBytes = doExport(exerciseWrapper);

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
                      criteriaBuilder.and(
                          criteriaBuilder.notEqual(root.get("id"), expected.getId()),
                          criteriaBuilder.equal(root.get("name"), expected.getName())))
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
      "Given a valid export zip file, given existing objects, new articles attached to imported exercise")
  @Test
  @WithMockUser(isAdmin = true)
  public void
      given_a_valid_export_zip_file_given_existing_objects_new_articles_attached_to_imported_exercise()
          throws Exception {
    ExerciseComposer.Composer exerciseWrapper = getExercise();
    byte[] zipBytes = doExport(exerciseWrapper);

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

  @DisplayName(
      "Given a valid export zip file, given existing objects, assign existing channels to imported exercise")
  @Test
  @WithMockUser(isAdmin = true)
  public void
      given_a_valid_export_zip_file_given_existing_objects_assign_existing_channels_to_imported_exercise()
          throws Exception {
    ExerciseComposer.Composer exerciseWrapper = getExercise();
    byte[] zipBytes = doExport(exerciseWrapper);

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
          dbChannels.stream().anyMatch(channel -> channel.getId().equals(expected.getId())),
          "Channel " + expected.getName() + " not found in imported exercise");
    }
  }

  @DisplayName(
      "Given a valid export zip file, given existing objects, assign existing tags to imported exercise")
  @Test
  @WithMockUser(isAdmin = true)
  public void
      given_a_valid_export_zip_file_given_existing_objects_assign_existing_tags_to_imported_exercise()
          throws Exception {
    ExerciseComposer.Composer exerciseWrapper = getExercise();
    byte[] zipBytes = doExport(exerciseWrapper);
    entityManager.flush();
    entityManager.clear();

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
          dbTags.stream().anyMatch(tag -> tag.getId().equals(expected.getId())),
          "Tag " + expected.getName() + " not found in imported exercise");
    }
  }

  @DisplayName(
      "Given a valid export zip file, given existing objects, create new objectives anyway")
  @Test
  @WithMockUser(isAdmin = true)
  public void given_a_valid_export_zip_file_given_existing_objects_create_new_objectives_anyway()
      throws Exception {
    ExerciseComposer.Composer exerciseWrapper = getExercise();
    byte[] zipBytes = doExport(exerciseWrapper);

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
          StreamSupport.stream(
                  objectiveRepository
                      .findAll(
                          (root, query, criteriaBuilder) ->
                              criteriaBuilder.and(
                                  criteriaBuilder.notEqual(root.get("id"), expected.getId()),
                                  criteriaBuilder.equal(root.get("title"), expected.getTitle())))
                      .spliterator(),
                  false)
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
      "Given a valid export zip file, given existing objects, new objectives attached to imported exercise")
  @Test
  @WithMockUser(isAdmin = true)
  public void
      given_a_valid_export_zip_file_given_existing_objects_new_objectives_attached_to_imported_exercise()
          throws Exception {
    ExerciseComposer.Composer exerciseWrapper = getExercise();
    byte[] zipBytes = doExport(exerciseWrapper);

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
      "Given a valid export zip file, given existing objects, create new lessons categories anyway")
  @Test
  @WithMockUser(isAdmin = true)
  public void
      given_a_valid_export_zip_file_given_existing_objects_create_new_lessons_categories_anyway()
          throws Exception {
    ExerciseComposer.Composer exerciseWrapper = getExercise();
    byte[] zipBytes = doExport(exerciseWrapper);

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
          StreamSupport.stream(
                  lessonsCategoryRepository
                      .findAll(
                          (root, query, criteriaBuilder) ->
                              criteriaBuilder.and(
                                  criteriaBuilder.notEqual(root.get("id"), expected.getId()),
                                  criteriaBuilder.equal(root.get("name"), expected.getName())))
                      .spliterator(),
                  false)
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
      "Given a valid export zip file, given existing objects, new lessons categories attached to imported exercise")
  @Test
  @WithMockUser(isAdmin = true)
  public void
      given_a_valid_export_zip_file_given_existing_objects_new_lessons_categories_attached_to_imported_exercise()
          throws Exception {
    ExerciseComposer.Composer exerciseWrapper = getExercise();
    byte[] zipBytes = doExport(exerciseWrapper);

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

  @DisplayName(
      "Given a valid export zip file, given existing objects, assign existing documents to imported exercise")
  @Test
  @WithMockUser(isAdmin = true)
  public void
      given_a_valid_export_zip_file_given_existing_objects_assign_existing_documents_to_imported_exercise()
          throws Exception {
    ExerciseComposer.Composer exerciseWrapper = getExercise();
    byte[] zipBytes = doExport(exerciseWrapper);

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
          dbDocs.stream().anyMatch(doc -> doc.getId().equals(expected.getId())),
          "Document " + expected.getTarget() + " not found in imported exercise");
    }
  }

  @DisplayName("Given a valid export zip file, given existing objects, create new injects anyway")
  @Test
  @WithMockUser(isAdmin = true)
  public void given_a_valid_export_zip_file_given_existing_objects_create_new_injects_anyway()
      throws Exception {
    ExerciseComposer.Composer exerciseWrapper = getExercise();
    byte[] zipBytes = doExport(exerciseWrapper);

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
                      criteriaBuilder.and(
                          criteriaBuilder.notEqual(root.get("id"), expected.getId()),
                          criteriaBuilder.equal(root.get("title"), expected.getTitle())))
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
      "Given a valid export zip file, given existing objects, new injects attached to imported exercise")
  @Test
  @WithMockUser(isAdmin = true)
  public void
      given_a_valid_export_zip_file_given_existing_objects_new_injects_attached_to_imported_exercise()
          throws Exception {
    ExerciseComposer.Composer exerciseWrapper = getExercise();
    byte[] zipBytes = doExport(exerciseWrapper);

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

  @DisplayName("Given a valid export zip file, given existing objects, create new variables anyway")
  @Test
  @WithMockUser(isAdmin = true)
  public void given_a_valid_export_zip_file_given_existing_objects_create_new_variables_anyway()
      throws Exception {
    ExerciseComposer.Composer exerciseWrapper = getExercise();
    byte[] zipBytes = doExport(exerciseWrapper);

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
          StreamSupport.stream(
                  variableRepository
                      .findAll(
                          (root, query, criteriaBuilder) ->
                              criteriaBuilder.and(
                                  criteriaBuilder.notEqual(root.get("id"), expected.getId()),
                                  criteriaBuilder.equal(root.get("key"), expected.getKey())))
                      .spliterator(),
                  false)
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
      "Given a valid export zip file, given existing objects, new variables attached to imported exercise")
  @Test
  @WithMockUser(isAdmin = true)
  public void
      given_a_valid_export_zip_file_given_existing_objects_new_variables_attached_to_imported_exercise()
          throws Exception {
    ExerciseComposer.Composer exerciseWrapper = getExercise();
    byte[] zipBytes = doExport(exerciseWrapper);

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

  @DisplayName(
      "Given a valid export zip file, given existing objects, assign existing challenges to imported exercise")
  @Test
  @WithMockUser(isAdmin = true)
  public void
      given_a_valid_export_zip_file_given_existing_objects_assign_existing_challenges_to_imported_exercise()
          throws Exception {
    ExerciseComposer.Composer exerciseWrapper = getExercise();
    byte[] zipBytes = doExport(exerciseWrapper);

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

    List<Inject> dbInject =
        findImportedExerciseFromDb(exerciseWrapper.get().getName()).getInjects();
    for (Challenge expected : challengeComposer.generatedItems) {
      Assertions.assertTrue(
          dbInject.stream()
              .anyMatch(inject -> inject.getContent().toString().contains(expected.getId())),
          "Challenge " + expected.getName() + " not found in imported exercise");
    }
  }
}
