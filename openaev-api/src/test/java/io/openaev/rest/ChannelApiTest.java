package io.openaev.rest;

import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Channel;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.ArticleRepository;
import io.openaev.database.repository.ChannelRepository;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.rest.channel.form.ArticleCreateInput;
import io.openaev.rest.channel.form.ArticleUpdateInput;
import io.openaev.rest.channel.form.ChannelCreateInput;
import io.openaev.rest.channel.form.ChannelUpdateInput;
import io.openaev.service.scenario.ScenarioService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utilstest.RabbitMQTestListener;
import jakarta.persistence.EntityManager;
import java.util.Set;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
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
  @Autowired private TenantIsolationTestHelper tenantIsolationHelper;
  @Autowired private EntityManager entityManager;

  static String SCENARIO_ID;
  static String CHANNEL_ID;
  static String ARTICLE_ID;

  @AfterAll
  void afterAll() {
    this.scenarioRepository.deleteById(SCENARIO_ID);
    this.channelRepository.deleteById(CHANNEL_ID);
    this.articleRepository.deleteById(ARTICLE_ID);
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
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
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
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
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
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
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
        .perform(delete(SCENARIO_URI + "/" + SCENARIO_ID + "/articles/" + ARTICLE_ID).with(csrf()))
        .andExpect(status().is2xxSuccessful());
  }

  // -- TENANT ISOLATION TESTS --

  @Nested
  @DisplayName("Tenant Isolation")
  @WithMockUser(isAdmin = true)
  @Transactional
  class TenantIsolation {

    private ChannelCreateInput createChannelInput(String name) {
      ChannelCreateInput input = new ChannelCreateInput();
      input.setName(name);
      input.setType("Journal");
      input.setDescription("Test channel description");
      return input;
    }

    private String createChannelInTenant(String tenantId, String name) throws Exception {
      String response =
          mvc.perform(
                  post("/api/tenants/" + tenantId + "/channels")
                      .content(asJsonString(createChannelInput(name)))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();
      return JsonPath.read(response, "$.channel_id");
    }

    @Test
    @DisplayName("Channel created in tenant X should NOT be readable from tenant Y")
    void given_channelInTenantX_should_notBeReadableFromTenantY() throws Exception {
      // Arrange
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X", Set.of(Capability.MANAGE_CHANNELS, Capability.ACCESS_CHANNELS));
      Tenant tenantY =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant Y", Set.of(Capability.ACCESS_CHANNELS));

      String channelId = createChannelInTenant(tenantX.getId(), "Read Isolation Channel");
      entityManager.flush();
      entityManager.clear();

      // Act — read from tenant Y
      // NOTE: build the path the same way as the other isolation tests below
      // ("/api/tenants/{tenantId}/channels/{id}"). Using CHANNEL_URI here would double the
      // "/api" prefix (CHANNEL_URI == "/api/channels") and 404 for the wrong reason, hiding
      // whether tenant isolation is actually enforced.
      int responseStatus =
          mvc.perform(
                  get("/api/tenants/" + tenantY.getId() + "/channels/" + channelId)
                      .accept(MediaType.APPLICATION_JSON))
              .andReturn()
              .getResponse()
              .getStatus();

      // Assert
      assertThat(responseStatus).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    @DisplayName("Channel created in tenant X should NOT be updatable from tenant Y")
    void given_channelInTenantX_should_notBeUpdatableFromTenantY() throws Exception {
      // Arrange
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X", Set.of(Capability.MANAGE_CHANNELS, Capability.ACCESS_CHANNELS));
      Tenant tenantY =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant Y", Set.of(Capability.MANAGE_CHANNELS, Capability.ACCESS_CHANNELS));

      String channelId = createChannelInTenant(tenantX.getId(), "Update Isolation Channel");
      entityManager.flush();
      entityManager.clear();

      // Act — update from tenant Y
      ChannelUpdateInput updateInput = new ChannelUpdateInput();
      updateInput.setName("Hijacked Channel");
      updateInput.setType("Journal");
      updateInput.setDescription("Hijacked description");

      int responseStatus =
          mvc.perform(
                  put("/api/tenants/" + tenantY.getId() + "/channels/" + channelId)
                      .content(asJsonString(updateInput))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andReturn()
              .getResponse()
              .getStatus();

      // Assert
      assertThat(responseStatus).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    @DisplayName("Channel created in tenant X should be updatable from tenant X")
    void given_channelInTenantX_should_beUpdatableFromTenantX() throws Exception {
      // Arrange
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X", Set.of(Capability.MANAGE_CHANNELS, Capability.ACCESS_CHANNELS));

      String channelId = createChannelInTenant(tenantX.getId(), "Same Tenant Channel");

      // Act — update from same tenant
      ChannelUpdateInput updateInput = new ChannelUpdateInput();
      updateInput.setName("Updated Channel");
      updateInput.setType("Blog");
      updateInput.setDescription("Updated description");

      String response =
          mvc.perform(
                  put("/api/tenants/" + tenantX.getId() + "/channels/" + channelId)
                      .content(asJsonString(updateInput))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      assertEquals("Updated Channel", JsonPath.read(response, "$.channel_name"));
    }

    @Test
    @DisplayName("Channel created in tenant X should NOT be deletable from tenant Y")
    void given_channelInTenantX_should_notBeDeletableFromTenantY() throws Exception {
      // Arrange
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X", Set.of(Capability.MANAGE_CHANNELS, Capability.ACCESS_CHANNELS));
      Tenant tenantY =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant Y", Set.of(Capability.DELETE_CHANNELS, Capability.ACCESS_CHANNELS));

      String channelId = createChannelInTenant(tenantX.getId(), "Delete Isolation Channel");
      entityManager.flush();
      entityManager.clear();

      // Act — delete from tenant Y
      int responseStatus =
          mvc.perform(
                  delete("/api/tenants/" + tenantY.getId() + "/channels/" + channelId).with(csrf()))
              .andReturn()
              .getResponse()
              .getStatus();

      // Assert
      assertThat(responseStatus).isEqualTo(HttpStatus.NOT_FOUND.value());
    }
  }
}
