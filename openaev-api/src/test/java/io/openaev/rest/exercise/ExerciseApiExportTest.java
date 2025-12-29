package io.openaev.rest.exercise;

import static io.openaev.rest.exercise.ExerciseApi.EXERCISE_URI;
import static io.openaev.utils.fixtures.FileFixture.WELL_KNOWN_FILES;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.database.model.Tag;
import io.openaev.export.Mixins;
import io.openaev.rest.exercise.exports.ExerciseFileExport;
import io.openaev.rest.exercise.exports.VariableMixin;
import io.openaev.rest.exercise.exports.VariableWithValueMixin;
import io.openaev.service.ArticleService;
import io.openaev.service.ChallengeService;
import io.openaev.service.FileService;
import io.openaev.utils.ZipUtils;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.composers.*;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.annotation.Resource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@TestInstance(PER_CLASS)
class ExerciseApiExportTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private ArticleComposer articleComposer;
  @Autowired private ChannelComposer channelComposer;
  @Autowired private InjectorFixture injectorFixture;
  @Autowired private LessonsQuestionsComposer lessonsQuestionsComposer;
  @Autowired private LessonsCategoryComposer lessonsCategoryComposer;
  @Autowired private VariableComposer variableComposer;
  @Autowired private PayloadComposer payloadComposer;
  @Autowired private TeamComposer teamComposer;
  @Autowired private UserComposer userComposer;
  @Autowired private OrganizationComposer organizationComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private ChallengeComposer challengeComposer;
  @Autowired private ObjectiveComposer objectiveComposer;
  @Autowired private DocumentComposer documentComposer;
  @Autowired private TagComposer tagComposer;
  @Autowired private DomainComposer domainComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private ChallengeService challengeService;
  @Autowired private ArticleService articleService;
  @Resource protected ObjectMapper mapper;
  @Autowired private FileService fileService;

  @BeforeEach
  void before() throws Exception {
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
    payloadComposer.reset();

    // delete the test files from the minio service
    for (String fileName : WELL_KNOWN_FILES.keySet()) {
      fileService.deleteFile(fileName);
    }
  }

  private Exercise getExercise() {
    Set<Domain> domains = domainComposer.forDomain(null).persist().getSet();

    return exerciseComposer
        .forExercise(ExerciseFixture.createDefaultCrisisExercise())
        .withArticle(
            articleComposer
                .forArticle(ArticleFixture.getArticleNoChannel())
                .withChannel(channelComposer.forChannel(ChannelFixture.getChannel())))
        .withLessonCategory(
            lessonsCategoryComposer
                .forLessonsCategory(LessonsCategoryFixture.createLessonCategory())
                .withLessonsQuestion(
                    lessonsQuestionsComposer.forLessonsQuestion(
                        LessonsQuestionFixture.createLessonsQuestion())))
        .withTeam(
            teamComposer
                .forTeam(TeamFixture.getEmptyTeam())
                .withOrganisation(
                    organizationComposer.forOrganization(
                        OrganizationFixture.createDefaultOrganisation()))
                .withTag(tagComposer.forTag(TagFixture.getTagWithText("Team tag")))
                .withUser(
                    userComposer
                        .forUser(UserFixture.getUser())
                        .withTag(tagComposer.forTag(TagFixture.getTagWithText("User tag")))
                        .withOrganization(
                            organizationComposer
                                .forOrganization(OrganizationFixture.createOrganization())
                                .withTag(
                                    tagComposer.forTag(
                                        TagFixture.getTagWithText("Organization tag"))))))
        .withTeamUsers()
        .withInject(
            injectComposer
                .forInject(InjectFixture.getInjectWithoutContract())
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
        .withInject(
            injectComposer
                .forInject(InjectFixture.getInjectWithoutContract())
                .withInjectorContract(
                    injectorContractComposer
                        .forInjectorContract(
                            InjectorContractFixture.createDefaultInjectorContract())
                        .withInjector(injectorFixture.getWellKnownOaevImplantInjector())
                        .withPayload(
                            payloadComposer
                                .forPayload(PayloadFixture.createDefaultFileDrop(domains))
                                .withFileDrop(
                                    documentComposer
                                        .forDocument(
                                            DocumentFixture.getDocument(
                                                FileFixture.getBadCoffeeFileContent()))
                                        .withInMemoryFile(FileFixture.getBadCoffeeFileContent())))))
        .withDocument(
            documentComposer
                .forDocument(DocumentFixture.getDocument(FileFixture.getPlainTextFileContent()))
                .withTag(tagComposer.forTag(TagFixture.getTagWithText("Document tag")))
                .withInMemoryFile(FileFixture.getPlainTextFileContent()))
        .withObjective(objectiveComposer.forObjective(ObjectiveFixture.getObjective()))
        .withTag(tagComposer.forTag(TagFixture.getTagWithText("Exercise tag")))
        .withVariable(variableComposer.forVariable(VariableFixture.getVariable()))
        .persist()
        .get();
  }

  private String getJsonExportFromZip(byte[] zipBytes, String entryName) throws IOException {
    return ZipUtils.getZipEntry(zipBytes, "%s.json".formatted(entryName), ZipUtils::streamToString);
  }

  @DisplayName("Given a valid simulation, the export file is found in zip and correct")
  @Test
  @WithMockUser(isAdmin = true)
  public void given_a_valid_simulation_the_export_file_is_found_in_zip_and_correct()
      throws Exception {
    Exercise ex = getExercise();
    byte[] response =
        mvc.perform(
                get(EXERCISE_URI + "/" + ex.getId() + "/export").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

    String actualJson = getJsonExportFromZip(response, ex.getName());
    ObjectMapper exportMapper = mapper.copy();
    String expectedJson =
        exportMapper.writeValueAsString(
            ExerciseFileExport.fromExercise(ex, exportMapper, challengeService, articleService)
                .withOptions(0));

    assertThatJson(expectedJson)
        .whenIgnoringPaths(
            "exercise_injects[*].inject_injector_contract.injector_contract_payload.payload_created_at",
            "exercise_injects[*].inject_injector_contract.injector_contract_payload.payload_updated_at",
            "exercise_injects[*].inject_injector_contract.injector_contract_created_at",
            "exercise_injects[*].inject_injector_contract.injector_contract_updated_at",
            "exercise_injects[*].inject_injector_contract.injector_contract_payload.payload_domains[*].domain_created_at",
            "exercise_injects[*].inject_injector_contract.injector_contract_payload.payload_domains[*].domain_updated_at",
            "exercise_injects[*].inject_injector_contract.injector_contract_domains[*].domain_created_at",
            "exercise_injects[*].inject_injector_contract.injector_contract_domains[*].domain_updated_at")
        .isObject()
        .isEqualTo(actualJson);
  }

  @DisplayName(
      "Given a valid simulation and full options, the export file is found in zip and correct")
  @Test
  @WithMockUser(isAdmin = true)
  public void
      given_a_valid_simulation_and_full_options_the_export_file_is_found_in_zip_and_correct()
          throws Exception {
    Exercise ex = getExercise();
    byte[] response =
        mvc.perform(
                get(EXERCISE_URI + "/" + ex.getId() + "/export")
                    .queryParam("isWithPlayers", "true")
                    .queryParam("isWithTeams", "true")
                    .queryParam("isWithVariableValues", "true")
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

    String actualJson = getJsonExportFromZip(response, ex.getName());
    ObjectMapper exportMapper = mapper.copy();
    String expectedJson =
        exportMapper.writeValueAsString(
            ExerciseFileExport.fromExercise(ex, exportMapper, challengeService, articleService)
                .withOptions(7));

    assertThatJson(expectedJson)
        .whenIgnoringPaths(
            "exercise_injects[*].inject_injector_contract.injector_contract_payload.payload_created_at",
            "exercise_injects[*].inject_injector_contract.injector_contract_payload.payload_updated_at",
            "exercise_injects[*].inject_injector_contract.injector_contract_created_at",
            "exercise_injects[*].inject_injector_contract.injector_contract_updated_at",
            "exercise_injects[*].inject_injector_contract.injector_contract_payload.payload_domains[*].domain_created_at",
            "exercise_injects[*].inject_injector_contract.injector_contract_payload.payload_domains[*].domain_updated_at",
            "exercise_injects[*].inject_injector_contract.injector_contract_domains[*].domain_created_at",
            "exercise_injects[*].inject_injector_contract.injector_contract_domains[*].domain_updated_at")
        .isObject()
        .isEqualTo(actualJson);
  }

  @DisplayName("Given a valid simulation and default options, exported tags are correct")
  @Test
  @WithMockUser(isAdmin = true)
  public void given_a_valid_simulation_and_default_options_exported_tags_are_correct()
      throws Exception {
    ObjectMapper objectMapper = mapper.copy();
    Exercise ex = getExercise();
    byte[] response =
        mvc.perform(
                get(EXERCISE_URI + "/" + ex.getId() + "/export").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

    String actualJson = getJsonExportFromZip(response, ex.getName());

    objectMapper.addMixIn(Tag.class, Mixins.Tag.class);
    objectMapper.addMixIn(Base.class, Mixins.Base.class);
    List<Tag> expectedTags =
        tagComposer.generatedItems.stream()
            .filter(
                tag ->
                    Arrays.asList(
                            "exercise tag",
                            "document tag",
                            "challenge tag",
                            "inject tag",
                            "organization tag")
                        .contains(tag.getName()))
            .toList();
    String tagsJson = objectMapper.writeValueAsString(expectedTags);

    assertThatJson(actualJson).when(IGNORING_ARRAY_ORDER).node("exercise_tags").isEqualTo(tagsJson);
  }

  @DisplayName("Given a valid simulation and default options, exported objectives are correct")
  @Test
  @WithMockUser(isAdmin = true)
  public void given_a_valid_simulation_and_default_options_exported_objectives_are_correct()
      throws Exception {
    ObjectMapper objectMapper = mapper.copy();
    Exercise ex = getExercise();
    byte[] response =
        mvc.perform(
                get(EXERCISE_URI + "/" + ex.getId() + "/export").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

    String actualJson = getJsonExportFromZip(response, ex.getName());

    objectMapper.addMixIn(Objective.class, Mixins.Objective.class);
    objectMapper.addMixIn(Base.class, Mixins.Base.class);
    String objectiveJson = objectMapper.writeValueAsString(objectiveComposer.generatedItems);

    assertThatJson(actualJson)
        .when(IGNORING_ARRAY_ORDER)
        .node("exercise_objectives")
        .isEqualTo(objectiveJson);
  }

  @DisplayName("Given a valid simulation and default options, exported challenges are correct")
  @Test
  @WithMockUser(isAdmin = true)
  public void given_a_valid_simulation_and_default_options_exported_challenges_are_correct()
      throws Exception {
    ObjectMapper objectMapper = mapper.copy();
    Exercise ex = getExercise();
    byte[] response =
        mvc.perform(
                get(EXERCISE_URI + "/" + ex.getId() + "/export").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

    String actualJson = getJsonExportFromZip(response, ex.getName());

    objectMapper.addMixIn(Challenge.class, Mixins.Challenge.class);
    objectMapper.addMixIn(Base.class, Mixins.Base.class);
    String challengeJson = objectMapper.writeValueAsString(challengeComposer.generatedItems);

    assertThatJson(actualJson)
        .when(IGNORING_ARRAY_ORDER)
        .node("exercise_challenges")
        .isEqualTo(challengeJson);
  }

  @DisplayName("Given a valid simulation and default options, exported articles are correct")
  @Test
  @WithMockUser(isAdmin = true)
  public void given_a_valid_simulation_and_default_options_exported_articles_are_correct()
      throws Exception {
    ObjectMapper objectMapper = mapper.copy();
    Exercise ex = getExercise();
    byte[] response =
        mvc.perform(
                get(EXERCISE_URI + "/" + ex.getId() + "/export").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

    String actualJson = getJsonExportFromZip(response, ex.getName());

    objectMapper.addMixIn(Article.class, Mixins.Article.class);
    objectMapper.addMixIn(Base.class, Mixins.Base.class);
    String articleJson = objectMapper.writeValueAsString(articleComposer.generatedItems);

    assertThatJson(actualJson)
        .when(IGNORING_ARRAY_ORDER)
        .node("exercise_articles")
        .isEqualTo(articleJson);
  }

  @DisplayName("Given a valid simulation and default options, exported channels are correct")
  @Test
  @WithMockUser(isAdmin = true)
  public void given_a_valid_simulation_and_default_options_exported_channels_are_correct()
      throws Exception {
    ObjectMapper objectMapper = mapper.copy();
    Exercise ex = getExercise();
    byte[] response =
        mvc.perform(
                get(EXERCISE_URI + "/" + ex.getId() + "/export").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

    String actualJson = getJsonExportFromZip(response, ex.getName());

    objectMapper.addMixIn(Channel.class, Mixins.Channel.class);
    objectMapper.addMixIn(Base.class, Mixins.Base.class);
    String channelJson = objectMapper.writeValueAsString(channelComposer.generatedItems);

    assertThatJson(actualJson)
        .when(IGNORING_ARRAY_ORDER)
        .node("exercise_channels")
        .isEqualTo(channelJson);
  }

  @DisplayName("Given a valid simulation and default options, exported documents are correct")
  @Test
  @WithMockUser(isAdmin = true)
  public void given_a_valid_simulation_and_default_options_exported_documents_are_correct()
      throws Exception {
    ObjectMapper objectMapper = mapper.copy();
    Exercise ex = getExercise();
    byte[] response =
        mvc.perform(
                get(EXERCISE_URI + "/" + ex.getId() + "/export").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

    String actualJson = getJsonExportFromZip(response, ex.getName());

    objectMapper.addMixIn(Document.class, Mixins.Document.class);
    objectMapper.addMixIn(Base.class, Mixins.Base.class);
    String documentJson = objectMapper.writeValueAsString(documentComposer.generatedItems);

    assertThatJson(actualJson)
        .when(IGNORING_ARRAY_ORDER)
        .node("exercise_documents")
        .isEqualTo(documentJson);
  }

  @DisplayName("Given a valid simulation and default options, exported exercise info are correct")
  @Test
  @WithMockUser(isAdmin = true)
  public void given_a_valid_simulation_and_default_options_exported_exercise_info_are_correct()
      throws Exception {
    ObjectMapper objectMapper = mapper.copy();
    Exercise ex = getExercise();
    byte[] response =
        mvc.perform(
                get(EXERCISE_URI + "/" + ex.getId() + "/export").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

    String actualJson = getJsonExportFromZip(response, ex.getName());

    objectMapper.addMixIn(Exercise.class, Mixins.Exercise.class);
    objectMapper.addMixIn(Base.class, Mixins.Base.class);
    String exerciseJson = objectMapper.writeValueAsString(ex);

    assertThatJson(actualJson)
        .when(IGNORING_ARRAY_ORDER)
        .node("exercise_information")
        .isEqualTo(exerciseJson);
  }

  @DisplayName("Given a valid simulation and default options, exported variables have no value")
  @Test
  @WithMockUser(isAdmin = true)
  public void given_a_valid_simulation_and_default_options_exported_variables_have_no_value()
      throws Exception {
    ObjectMapper objectMapper = mapper.copy();
    Exercise ex = getExercise();
    byte[] response =
        mvc.perform(
                get(EXERCISE_URI + "/" + ex.getId() + "/export").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

    String actualJson = getJsonExportFromZip(response, ex.getName());

    objectMapper.addMixIn(Variable.class, VariableMixin.class);
    objectMapper.addMixIn(Base.class, Mixins.Base.class);
    String variableJson = objectMapper.writeValueAsString(variableComposer.generatedItems);

    assertThatJson(actualJson)
        .when(IGNORING_ARRAY_ORDER)
        .node("exercise_variables")
        .isEqualTo(variableJson);
  }

  @DisplayName(
      "Given a valid simulation, given isWithVariableValues options, exported variables have values")
  @Test
  @WithMockUser(isAdmin = true)
  public void
      given_a_valid_simulation_given_isWithVariableValues_option_exported_variables_have_values()
          throws Exception {
    ObjectMapper objectMapper = mapper.copy();
    Exercise ex = getExercise();
    byte[] response =
        mvc.perform(
                get(EXERCISE_URI + "/" + ex.getId() + "/export")
                    .queryParam("isWithVariableValues", "true")
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

    String actualJson = getJsonExportFromZip(response, ex.getName());

    objectMapper.addMixIn(Variable.class, VariableWithValueMixin.class);
    objectMapper.addMixIn(Base.class, Mixins.Base.class);
    String variableJson = objectMapper.writeValueAsString(variableComposer.generatedItems);

    assertThatJson(actualJson)
        .when(IGNORING_ARRAY_ORDER)
        .node("exercise_variables")
        .isEqualTo(variableJson);
  }

  @DisplayName("Given a valid simulation and default options, exported teams is empty array")
  @Test
  @WithMockUser(isAdmin = true)
  public void given_a_valid_simulation_and_default_options_exported_teams_is_empty_array()
      throws Exception {
    Exercise ex = getExercise();
    byte[] response =
        mvc.perform(
                get(EXERCISE_URI + "/" + ex.getId() + "/export").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

    String actualJson = getJsonExportFromZip(response, ex.getName());

    assertThatJson(actualJson).when(IGNORING_ARRAY_ORDER).node("exercise_teams").isEqualTo("[]");
  }

  @DisplayName(
      "Given a valid simulation, given isWithTeams and NOT isWithPlayers options, exported teams have empty users")
  @Test
  @WithMockUser(isAdmin = true)
  public void
      given_a_valid_simulation_given_isWithTeams_and_not_isWithPlayers_options_exported_teams_have_empty_users()
          throws Exception {
    ObjectMapper objectMapper = mapper.copy();
    Exercise ex = getExercise();
    byte[] response =
        mvc.perform(
                get(EXERCISE_URI + "/" + ex.getId() + "/export")
                    .queryParam("isWithTeams", "true")
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

    String actualJson = getJsonExportFromZip(response, ex.getName());

    objectMapper.addMixIn(Team.class, Mixins.EmptyTeam.class);
    objectMapper.addMixIn(Base.class, Mixins.Base.class);
    String teamsJson = objectMapper.writeValueAsString(teamComposer.generatedItems);

    assertThatJson(actualJson)
        .when(IGNORING_ARRAY_ORDER)
        .node("exercise_teams")
        .isEqualTo(teamsJson);
    // users still not included
    assertThatJson(actualJson).when(IGNORING_ARRAY_ORDER).node("exercise_users").isAbsent();
  }

  @DisplayName("Given a valid simulation and default options, exported users is absent key")
  @Test
  @WithMockUser(isAdmin = true)
  public void given_a_valid_simulation_and_default_options_exported_users_is_absent_key()
      throws Exception {
    Exercise ex = getExercise();
    byte[] response =
        mvc.perform(
                get(EXERCISE_URI + "/" + ex.getId() + "/export").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

    String actualJson = getJsonExportFromZip(response, ex.getName());

    assertThatJson(actualJson).when(IGNORING_ARRAY_ORDER).node("exercise_users").isAbsent();
  }

  @DisplayName(
      "Given a valid simulation, given isWithPlayers and NOT isWithTeams option, exported users are correct")
  @Test
  @WithMockUser(isAdmin = true)
  public void
      given_a_valid_simulation_given_isWithPlayers_and_not_isWithTeams_options_exported_users_are_correct()
          throws Exception {
    ObjectMapper objectMapper = mapper.copy();
    Exercise ex = getExercise();
    byte[] response =
        mvc.perform(
                get(EXERCISE_URI + "/" + ex.getId() + "/export")
                    .queryParam("isWithPlayers", "true")
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

    String actualJson = getJsonExportFromZip(response, ex.getName());

    objectMapper.addMixIn(User.class, Mixins.User.class);
    objectMapper.addMixIn(Base.class, Mixins.Base.class);
    String usersJson = objectMapper.writeValueAsString(userComposer.generatedItems);

    assertThatJson(actualJson)
        .when(IGNORING_ARRAY_ORDER)
        .node("exercise_users")
        .isEqualTo(usersJson);
    assertThatJson(actualJson).when(IGNORING_ARRAY_ORDER).node("exercise_teams").isEqualTo("[]");
  }

  @DisplayName("Given a valid simulation and default options, exported organisations is absent key")
  @Test
  @WithMockUser(isAdmin = true)
  public void given_a_valid_simulation_and_default_options_exported_organisations_is_absent_key()
      throws Exception {
    Exercise ex = getExercise();
    byte[] response =
        mvc.perform(
                get(EXERCISE_URI + "/" + ex.getId() + "/export").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

    String actualJson = getJsonExportFromZip(response, ex.getName());

    assertThatJson(actualJson).when(IGNORING_ARRAY_ORDER).node("exercise_users").isAbsent();
  }

  @DisplayName(
      "Given a valid simulation, given isWithPlayers option, exported organisations are correct")
  @Test
  @WithMockUser(isAdmin = true)
  public void
      given_a_valid_simulation_given_isWithPlayers_option_exported_organisations_are_correct()
          throws Exception {
    ObjectMapper objectMapper = mapper.copy();
    Exercise ex = getExercise();
    byte[] response =
        mvc.perform(
                get(EXERCISE_URI + "/" + ex.getId() + "/export")
                    .queryParam("isWithPlayers", "true")
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

    String actualJson = getJsonExportFromZip(response, ex.getName());

    objectMapper.addMixIn(Organization.class, Mixins.Organization.class);
    objectMapper.addMixIn(Base.class, Mixins.Base.class);
    String orgJson = objectMapper.writeValueAsString(organizationComposer.generatedItems);

    assertThatJson(actualJson)
        .when(IGNORING_ARRAY_ORDER)
        .node("exercise_organizations")
        .isEqualTo(orgJson);
  }

  @DisplayName("Given documents are provided, exported archive contains the documents")
  @Test
  @WithMockUser(isAdmin = true)
  public void given_documents_are_provided_exported_archive_contains_the_documents()
      throws Exception {
    Exercise ex = getExercise();
    byte[] response =
        mvc.perform(
                get(EXERCISE_URI + "/" + ex.getId() + "/export")
                    .queryParam("isWithPlayers", "true")
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

    for (Document document : documentComposer.generatedItems) {
      try (ByteArrayInputStream fis =
          new ByteArrayInputStream(WELL_KNOWN_FILES.get(document.getTarget()).getContentBytes())) {
        byte[] docFromZip =
            ZipUtils.getZipEntry(response, document.getTarget(), ZipUtils::streamToBytes);
        byte[] docFromDisk = fis.readAllBytes();

        Assertions.assertArrayEquals(docFromZip, docFromDisk);
      }
    }
  }
}
