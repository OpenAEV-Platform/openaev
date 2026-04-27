package io.openaev.rest;

import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Channel;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.ArticleRepository;
import io.openaev.database.repository.ChannelRepository;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.rest.channel.form.ArticleCreateInput;
import io.openaev.rest.channel.form.ArticleUpdateInput;
import io.openaev.service.scenario.ScenarioService;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.fixtures.tenants.TenantComposer;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utilstest.RabbitMQTestListener;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(PER_CLASS)
class ChannelApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;

  @Autowired private ScenarioService scenarioService;
  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private ChannelRepository channelRepository;
  @Autowired private ArticleRepository articleRepository;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private TenantComposer tenantComposer;

  static String SCENARIO_ID;
  static String CHANNEL_ID;
  static String ARTICLE_ID;

  @AfterAll
  void afterAll() {
    if (SCENARIO_ID != null) {
      this.scenarioRepository.deleteById(SCENARIO_ID);
    }
    if (CHANNEL_ID != null) {
      this.channelRepository.deleteById(CHANNEL_ID);
    }
    if (ARTICLE_ID != null) {
      this.articleRepository.deleteById(ARTICLE_ID);
    }
  }

  // -- SCENARIOS --

  @DisplayName("Create article for scenario")
  @Test
  @Order(1)
  @WithMockUser(isAdmin = true)
  void createArticleForScenarioTest() throws Exception {
    // -- PREPARE --
    Scenario scenario = new Scenario();
    scenario.setName("Scenario name");
    Scenario scenarioCreated = this.scenarioService.createScenario(scenario);
    SCENARIO_ID = scenarioCreated.getId();

    Channel channel = new Channel();
    channel.setName("A channel");
    channel = this.channelRepository.save(channel);
    CHANNEL_ID = channel.getId();

    ArticleCreateInput articleCreateInput = new ArticleCreateInput();
    String articleName = "My article";
    articleCreateInput.setName(articleName);
    articleCreateInput.setChannelId(CHANNEL_ID);

    // -- EXECUTE --
    String response =
        this.mvc
            .perform(
                post(SCENARIO_URI + "/" + SCENARIO_ID + "/articles")
                    .content(asJsonString(articleCreateInput))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    assertNotNull(response);
    assertEquals(articleName, JsonPath.read(response, "$.article_name"));
    ARTICLE_ID = JsonPath.read(response, "$.article_id");
  }

  @DisplayName("Retrieve articles for scenario")
  @Test
  @Order(2)
  @WithMockUser(isAdmin = true)
  void retrieveArticlesForScenarioTest() throws Exception {
    // -- EXECUTE --
    String response =
        this.mvc
            .perform(
                get(SCENARIO_URI + "/" + SCENARIO_ID + "/articles")
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    assertNotNull(response);
  }

  @DisplayName("Update article for scenario")
  @Test
  @Order(3)
  @WithMockUser(isAdmin = true)
  void updateArticleForScenarioTest() throws Exception {
    // -- PREPARE --
    ArticleUpdateInput articleUpdateInput = new ArticleUpdateInput();
    String articleName = "My first article";
    articleUpdateInput.setName(articleName);
    articleUpdateInput.setChannelId(CHANNEL_ID);

    // -- EXECUTE --
    String response =
        this.mvc
            .perform(
                put(SCENARIO_URI + "/" + SCENARIO_ID + "/articles/" + ARTICLE_ID)
                    .content(asJsonString(articleUpdateInput))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    assertNotNull(response);
    assertEquals(articleName, JsonPath.read(response, "$.article_name"));
  }

  @DisplayName("Delete article for scenario")
  @Test
  @Order(4)
  @WithMockUser(isAdmin = true)
  void deleteArticleForScenarioTest() throws Exception {
    // -- EXECUTE 1 ASSERT --
    this.mvc
        .perform(delete(SCENARIO_URI + "/" + SCENARIO_ID + "/articles/" + ARTICLE_ID))
        .andExpect(status().is2xxSuccessful());
  }

  @Nested
  @DisplayName("Tenant isolation on scenario articles")
  @Transactional
  class TenantIsolation {

    @Test
    @DisplayName(
        "given scenario in Tenant XXX, when create article from Tenant YYY, should return 404")
    @WithMockUser(isAdmin = true)
    void given_scenarioInTenantXXX_when_createArticleFromTenantYYY_should_return404()
        throws Exception {
      // Arrange
      Tenant tenantXXX =
          tenantComposer.forTenant(TenantFixture.getTenant("Tenant XXX")).persist().get();
      Tenant tenantYYY =
          tenantComposer.forTenant(TenantFixture.getTenant("Tenant YYY")).persist().get();
      entityManager.flush();

      TenantContext.setCurrentTenant(tenantXXX.getId());
      Scenario scenario =
          scenarioComposer
              .forScenario(ScenarioFixture.createDefaultCrisisScenario())
              .persist()
              .get();
      Channel channel = new Channel();
      channel.setName("Tenant-Channel");
      channel = channelRepository.save(channel);
      entityManager.flush();
      entityManager.clear();
      String scenarioId = scenario.getId();

      // Act — switch to Tenant YYY
      TenantContext.setCurrentTenant(tenantYYY.getId());
      ArticleCreateInput input = new ArticleCreateInput();
      input.setName("Cross-tenant article");
      input.setChannelId(channel.getId());

      // Assert
      mvc.perform(
              post(SCENARIO_URI + "/" + scenarioId + "/articles")
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isNotFound());

      // Cleanup
      TenantContext.setCurrentTenant(tenantXXX.getId());
      scenarioComposer.reset();
      tenantComposer.reset();
      TenantContext.clearCurrentTenant();
    }

    @Test
    @DisplayName(
        "given scenario with article in Tenant XXX, when list articles from Tenant YYY, should return 404")
    @WithMockUser(isAdmin = true)
    void given_scenarioInTenantXXX_when_listArticlesFromTenantYYY_should_return404()
        throws Exception {
      // Arrange
      Tenant tenantXXX =
          tenantComposer.forTenant(TenantFixture.getTenant("Tenant XXX")).persist().get();
      Tenant tenantYYY =
          tenantComposer.forTenant(TenantFixture.getTenant("Tenant YYY")).persist().get();
      entityManager.flush();

      TenantContext.setCurrentTenant(tenantXXX.getId());
      Scenario scenario =
          scenarioComposer
              .forScenario(ScenarioFixture.createDefaultCrisisScenario())
              .persist()
              .get();
      entityManager.flush();
      entityManager.clear();
      String scenarioId = scenario.getId();

      // Act — switch to Tenant YYY
      TenantContext.setCurrentTenant(tenantYYY.getId());

      // Assert
      mvc.perform(
              get(SCENARIO_URI + "/" + scenarioId + "/articles").accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isNotFound());

      // Cleanup
      TenantContext.setCurrentTenant(tenantXXX.getId());
      scenarioComposer.reset();
      tenantComposer.reset();
      TenantContext.clearCurrentTenant();
    }

    @Test
    @DisplayName(
        "given scenario with article in Tenant XXX, when list articles from same tenant, should return 200")
    @WithMockUser(isAdmin = true)
    void given_scenarioInTenantXXX_when_listArticlesFromSameTenant_should_return200()
        throws Exception {
      // Arrange
      Tenant tenantXXX =
          tenantComposer.forTenant(TenantFixture.getTenant("Tenant XXX")).persist().get();
      entityManager.flush();

      TenantContext.setCurrentTenant(tenantXXX.getId());
      Scenario scenario =
          scenarioComposer
              .forScenario(ScenarioFixture.createDefaultCrisisScenario())
              .persist()
              .get();
      entityManager.flush();
      entityManager.clear();
      String scenarioId = scenario.getId();

      // Act & Assert — reading from same tenant should succeed
      mvc.perform(
              get(SCENARIO_URI + "/" + scenarioId + "/articles").accept(MediaType.APPLICATION_JSON))
          .andExpect(status().is2xxSuccessful());

      // Cleanup
      scenarioComposer.reset();
      tenantComposer.reset();
      TenantContext.clearCurrentTenant();
    }

    @Test
    @DisplayName(
        "given scenario in Tenant XXX, when delete article from Tenant YYY, should return 404")
    @WithMockUser(isAdmin = true)
    void given_scenarioInTenantXXX_when_deleteArticleFromTenantYYY_should_return404()
        throws Exception {
      // Arrange
      Tenant tenantXXX =
          tenantComposer.forTenant(TenantFixture.getTenant("Tenant XXX")).persist().get();
      Tenant tenantYYY =
          tenantComposer.forTenant(TenantFixture.getTenant("Tenant YYY")).persist().get();
      entityManager.flush();

      TenantContext.setCurrentTenant(tenantXXX.getId());
      Scenario scenario =
          scenarioComposer
              .forScenario(ScenarioFixture.createDefaultCrisisScenario())
              .persist()
              .get();
      entityManager.flush();
      entityManager.clear();
      String scenarioId = scenario.getId();

      // Act — switch to Tenant YYY
      TenantContext.setCurrentTenant(tenantYYY.getId());

      // Assert
      mvc.perform(delete(SCENARIO_URI + "/" + scenarioId + "/articles/fake-article-id"))
          .andExpect(status().isNotFound());

      // Cleanup
      TenantContext.setCurrentTenant(tenantXXX.getId());
      scenarioComposer.reset();
      tenantComposer.reset();
      TenantContext.clearCurrentTenant();
    }
  }
}
