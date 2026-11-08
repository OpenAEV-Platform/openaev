package io.openaev.rest.user;

import static io.openaev.rest.user.PlayerApi.PLAYER_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.database.audit.BaseEvent;
import io.openaev.database.model.Organization;
import io.openaev.database.model.Tag;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.OrganizationRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.rest.user.form.player.PlayerInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.OrganizationFixture;
import io.openaev.utils.fixtures.PlayerFixture;
import io.openaev.utils.fixtures.TagFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.composers.UserComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the activity stream is told when a player is written. The stream serializes the very
 * instance a lifecycle event holds, after the request is over, once per consumer of every tenant.
 * So the instance must not carry state that belongs to the response of one request:
 *
 * <ul>
 *   <li>a player created in B on {@code X-Tenant-Ids: B} must not show B's organization when the
 *       event is serialized for a consumer of the default tenant;
 *   <li>a known player upserted with a new tag must be streamed with that tag, not with the links
 *       it had before the request.
 * </ul>
 *
 * The events are read from the recorded application events and serialized the way {@code
 * StreamService} does, with the ambient tenant of a consumer of the default tenant.
 */
@TestInstance(PER_CLASS)
@Transactional
@RecordApplicationEvents
@DisplayName("Stream events of a player write")
public class PlayerWriteStreamEventTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  private static final String TENANT_HEADER = "X-Tenant-Ids";

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper mapper;
  @Autowired private ApplicationEvents applicationEvents;
  @Autowired private TenantIsolationTestHelper tenantIsolationHelper;
  @Autowired private UserComposer userComposer;
  @Autowired private TagRepository tagRepository;
  @Autowired private OrganizationRepository organizationRepository;

  private String tenantB;

  @BeforeEach
  void callerOfTheDefaultTenantAndB() throws Exception {
    tenantIsolationHelper.attachCurrentUserToTenant(Tenant.DEFAULT_TENANT_UUID);
    tenantB = tenantIsolationHelper.createTenantWithCurrentUser("stream-b").getId();
  }

  @AfterEach
  void clearAmbientTenant() {
    TenantContext.clearCurrentTenant();
  }

  /** The events about the user, serialized as a consumer of the default tenant would get them. */
  private List<JsonNode> streamedInstancesOf(String userId) {
    TenantContext.clearCurrentTenant();
    return applicationEvents.stream(BaseEvent.class)
        .filter(event -> event.getInstance() instanceof User user && userId.equals(user.getId()))
        .map(event -> mapper.<JsonNode>valueToTree(event.getInstance()))
        .toList();
  }

  @Nested
  @DisplayName("Player created in the tenant the header names")
  class CreatedInB {

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("The streamed player does not show the organization of B to the default tenant")
    void given_headerNamingTenantB_should_notStreamTheOrganizationOfB() throws Exception {
      // -- PREPARE --
      Organization organization = OrganizationFixture.createDefaultOrganisation();
      organization.setTenant(new Tenant(tenantB));
      String organizationId = organizationRepository.save(organization).getId();
      PlayerInput input = PlayerFixture.createPlayerInput();
      input.setEmail("stream-" + UUID.randomUUID() + "@filigran.io");
      input.setOrganizationId(organizationId);
      entityManager.flush();
      entityManager.clear();
      TenantContext.clearCurrentTenant();

      // -- EXECUTE --
      String response =
          mvc.perform(
                  post(PLAYER_URI)
                      .header(TENANT_HEADER, tenantB)
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- ASSERT --
      JsonNode created = mapper.readTree(response);
      assertEquals(organizationId, created.path("user_organization").asText());
      List<JsonNode> streamed = streamedInstancesOf(created.path("user_id").asText());
      assertFalse(streamed.isEmpty(), "The create must have published an event for the player");
      streamed.forEach(
          instance ->
              assertTrue(
                  instance.path("user_organization").isNull()
                      || instance.path("user_organization").isMissingNode(),
                  "The default tenant's consumers must not receive B's organization"));
    }
  }

  @Nested
  @DisplayName("Known player upserted with a new tag")
  class UpsertedWithATag {

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("The streamed player carries the tag the upsert added")
    void given_upsertAddingATag_should_streamThePlayerWithTheTag() throws Exception {
      // -- PREPARE --
      User player = userComposer.forUser(UserFixture.getUserWithDefaultEmail()).persist().get();
      entityManager.flush();
      tenantIsolationHelper.attachUserToTenant(player.getId(), Tenant.DEFAULT_TENANT_UUID);
      Tag tag = TagFixture.getTagWithText("stream-" + UUID.randomUUID());
      tag.setTenant(new Tenant(Tenant.DEFAULT_TENANT_UUID));
      String tagId = tagRepository.save(tag).getId();
      PlayerInput input = PlayerFixture.createPlayerInput();
      input.setEmail(player.getEmail());
      input.setFirstname("Changed-" + UUID.randomUUID());
      input.setTagIds(List.of(tagId));
      input.setTeamIds(List.of());
      entityManager.flush();
      entityManager.clear();
      TenantContext.clearCurrentTenant();

      // -- EXECUTE --
      mvc.perform(
              post(PLAYER_URI + "/upsert")
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());

      // -- ASSERT --
      List<JsonNode> streamed =
          streamedInstancesOf(player.getId()).stream()
              .filter(instance -> instance.path("user_firstname").asText().startsWith("Changed-"))
              .toList();
      assertFalse(streamed.isEmpty(), "The upsert must have published an update of the player");
      streamed.forEach(
          instance ->
              assertEquals(
                  List.of(tagId),
                  mapper.convertValue(
                      instance.path("user_tags"),
                      mapper.getTypeFactory().constructCollectionType(List.class, String.class)),
                  "The streamed player must carry the added tag"));
    }
  }
}
