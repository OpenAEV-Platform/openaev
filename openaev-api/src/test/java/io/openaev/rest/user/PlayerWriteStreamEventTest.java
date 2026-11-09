package io.openaev.rest.user;

import static io.openaev.rest.user.PlayerApi.PLAYER_URI;
import static io.openaev.rest.user.TenantUserApi.USER_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.api.users.dto.UserInput;
import io.openaev.config.OpenAEVPrincipal;
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
import io.openaev.service.PermissionService;
import io.openaev.service.StreamService;
import io.openaev.service.UserService;
import io.openaev.service.attackpath.AttackPathAccessControl;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.OrganizationFixture;
import io.openaev.utils.fixtures.PlayerFixture;
import io.openaev.utils.fixtures.TagFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.composers.UserComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.FluxSink;

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
  @Autowired private UserService userService;
  @Autowired private AttackPathAccessControl attackPathAccessControl;

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

  @SuppressWarnings("unchecked")
  private FluxSink<Object> consumerOf(StreamService stream, String sessionId, String tenantId)
      throws Exception {
    OpenAEVPrincipal principal = mock(OpenAEVPrincipal.class);
    when(principal.getId()).thenReturn(userService.currentUser().getId());
    FluxSink<Object> sink = mock(FluxSink.class);
    Class<?> consumerClass = Class.forName("io.openaev.service.StreamService$StreamConsumer");
    RecordComponent[] components = consumerClass.getRecordComponents();
    Constructor<?> constructor =
        consumerClass.getDeclaredConstructor(
            components[0].getType(), components[1].getType(), components[2].getType());
    constructor.setAccessible(true);
    ((Map<String, Object>) ReflectionTestUtils.getField(stream, "consumers"))
        .put(sessionId, constructor.newInstance(principal, tenantId, sink));
    return sink;
  }

  /** The instances the consumer was sent, as they travel on the wire. */
  @SuppressWarnings("unchecked")
  private List<JsonNode> receivedBy(FluxSink<Object> sink) {
    ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    verify(sink, atLeast(0)).next(captor.capture());
    List<JsonNode> instances = new ArrayList<>();
    for (Object sent : captor.getAllValues()) {
      JsonNode wire = mapper.valueToTree(((ServerSentEvent<BaseEvent>) sent).data());
      instances.add(wire.path("instance"));
    }
    return instances;
  }

  private String organizationOfB() {
    Organization organization = OrganizationFixture.createDefaultOrganisation();
    organization.setTenant(new Tenant(tenantB));
    return organizationRepository.save(organization).getId();
  }

  /** Sends the write, then streams its events to a consumer of each tenant and checks them. */
  private void assertOrganizationOfBStreamedToTheConsumersOfBOnly(
      MockHttpServletRequestBuilder write, String organizationId) throws Exception {
    // The permission check runs in a transaction of its own in production, where each consumer
    // gets its tenant scope; this test shares one transaction, so it is not what is under test.
    PermissionService permissions = mock(PermissionService.class);
    when(permissions.hasPermissionInTenant(any(), any(), any(), any(), any())).thenReturn(true);
    StreamService stream =
        new StreamService(permissions, userService, attackPathAccessControl, mapper);
    FluxSink<Object> sinkOfDefault = consumerOf(stream, "default", Tenant.DEFAULT_TENANT_UUID);
    FluxSink<Object> sinkOfB = consumerOf(stream, "b", tenantB);

    long publishedBefore = applicationEvents.stream(BaseEvent.class).count();
    String userId =
        mapper
            .readTree(
                mvc.perform(
                        write
                            .header(TENANT_HEADER, tenantB)
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.APPLICATION_JSON)
                            .with(csrf()))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .path("user_id")
            .asText();
    List<BaseEvent> events =
        applicationEvents.stream(BaseEvent.class)
            .skip(publishedBefore)
            .filter(
                event -> event.getInstance() instanceof User user && userId.equals(user.getId()))
            .toList();
    assertFalse(events.isEmpty(), "The write must have published an event for the user");
    events.forEach(stream::listenDatabaseUpdate);

    List<JsonNode> receivedByB = receivedBy(sinkOfB);
    assertFalse(receivedByB.isEmpty(), "The consumer of B must receive the user");
    receivedByB.forEach(
        instance ->
            assertEquals(
                organizationId,
                instance.path("user_organization").asText(),
                "The consumer of B must receive the organization of B"));
    List<JsonNode> receivedByDefault = receivedBy(sinkOfDefault);
    assertFalse(receivedByDefault.isEmpty(), "The consumer of the default tenant gets the user");
    receivedByDefault.forEach(
        instance ->
            assertFalse(
                instance.has("user_organization"),
                "The consumer of the default tenant must keep the organization it holds"));
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

  @Nested
  @DisplayName("Player created in B, streamed to the consumers of both tenants")
  class FanOut {

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("Each consumer gets the organization of its own tenant, never another's")
    void given_headerNamingTenantB_should_streamTheOrganizationOfBToTheConsumersOfB()
        throws Exception {
      // -- PREPARE --
      Organization organization = OrganizationFixture.createDefaultOrganisation();
      organization.setTenant(new Tenant(tenantB));
      String organizationId = organizationRepository.save(organization).getId();
      PlayerInput input = PlayerFixture.createPlayerInput();
      input.setEmail("fan-out-" + UUID.randomUUID() + "@filigran.io");
      input.setOrganizationId(organizationId);
      entityManager.flush();
      entityManager.clear();
      TenantContext.clearCurrentTenant();
      // The permission check runs in a transaction of its own in production, where each consumer
      // gets its tenant scope; this test shares one transaction, so it is not what is under test.
      PermissionService permissions = mock(PermissionService.class);
      when(permissions.hasPermissionInTenant(any(), any(), any(), any(), any())).thenReturn(true);
      StreamService stream =
          new StreamService(permissions, userService, attackPathAccessControl, mapper);
      FluxSink<Object> sinkOfDefault = consumerOf(stream, "default", Tenant.DEFAULT_TENANT_UUID);
      FluxSink<Object> sinkOfB = consumerOf(stream, "b", tenantB);

      // -- EXECUTE --
      String userId =
          mapper
              .readTree(
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
                      .getContentAsString())
              .path("user_id")
              .asText();
      List<BaseEvent> events =
          applicationEvents.stream(BaseEvent.class)
              .filter(
                  event -> event.getInstance() instanceof User user && userId.equals(user.getId()))
              .toList();
      assertFalse(events.isEmpty(), "The create must have published an event for the player");
      events.forEach(stream::listenDatabaseUpdate);

      // -- ASSERT --
      List<JsonNode> receivedByB = receivedBy(sinkOfB);
      assertFalse(receivedByB.isEmpty(), "The consumer of B must receive the player");
      receivedByB.forEach(
          instance ->
              assertEquals(
                  organizationId,
                  instance.path("user_organization").asText(),
                  "The consumer of B must receive the organization of B"));
      List<JsonNode> receivedByDefault = receivedBy(sinkOfDefault);
      assertFalse(
          receivedByDefault.isEmpty(), "The consumer of the default tenant gets the player");
      receivedByDefault.forEach(
          instance ->
              assertFalse(
                  instance.has("user_organization"),
                  "The consumer of the default tenant must keep the organization it holds"));
    }
  }

  @Nested
  @DisplayName("Known player written in B, streamed to the consumers of both tenants")
  class PlayerWriterFanOut {

    private PlayerInput inputFor(User player, String organizationId) {
      PlayerInput input = PlayerFixture.createPlayerInput();
      input.setEmail(player.getEmail());
      input.setFirstname("Changed-" + UUID.randomUUID());
      input.setOrganizationId(organizationId);
      input.setTagIds(List.of());
      input.setTeamIds(List.of());
      return input;
    }

    private User playerOf(String... tenantIds) {
      User player = userComposer.forUser(UserFixture.getUserWithDefaultEmail()).persist().get();
      entityManager.flush();
      for (String tenantId : tenantIds) {
        tenantIsolationHelper.attachUserToTenant(player.getId(), tenantId);
      }
      entityManager.flush();
      entityManager.clear();
      TenantContext.clearCurrentTenant();
      return player;
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("An update names B: the organization of B reaches the consumers of B only")
    void given_playerUpdateWithHeaderNamingB_should_streamTheOrganizationToTheConsumersOfB()
        throws Exception {
      // -- PREPARE --
      String organizationId = organizationOfB();
      User player = playerOf(Tenant.DEFAULT_TENANT_UUID, tenantB);

      // -- EXECUTE AND ASSERT --
      assertOrganizationOfBStreamedToTheConsumersOfBOnly(
          put(PLAYER_URI + "/" + player.getId())
              .content(asJsonString(inputFor(player, organizationId))),
          organizationId);
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("An upsert names B: the organization of B reaches the consumers of B only")
    void given_playerUpsertWithHeaderNamingB_should_streamTheOrganizationToTheConsumersOfB()
        throws Exception {
      // -- PREPARE --
      String organizationId = organizationOfB();
      User player = playerOf(Tenant.DEFAULT_TENANT_UUID);

      // -- EXECUTE AND ASSERT --
      assertOrganizationOfBStreamedToTheConsumersOfBOnly(
          post(PLAYER_URI + "/upsert").content(asJsonString(inputFor(player, organizationId))),
          organizationId);
    }
  }

  @Nested
  @DisplayName("User written in B, streamed to the consumers of both tenants")
  class UserFanOut {

    private UserInput userInput(String email, String organizationId) {
      return new UserInput(
          email,
          "Changed",
          "User",
          UserFixture.RAW_PASSWORD,
          null,
          null,
          null,
          organizationId,
          List.of(),
          false,
          List.of());
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("An update names B: the organization of B reaches the consumers of B only")
    void given_userUpdateWithHeaderNamingB_should_streamTheOrganizationToTheConsumersOfB()
        throws Exception {
      // -- PREPARE --
      String organizationId = organizationOfB();
      User member = userComposer.forUser(UserFixture.getUserWithDefaultEmail()).persist().get();
      entityManager.flush();
      tenantIsolationHelper.attachUserToTenant(member.getId(), Tenant.DEFAULT_TENANT_UUID);
      tenantIsolationHelper.attachUserToTenant(member.getId(), tenantB);
      entityManager.flush();
      entityManager.clear();
      TenantContext.clearCurrentTenant();

      // -- EXECUTE AND ASSERT --
      assertOrganizationOfBStreamedToTheConsumersOfBOnly(
          put(USER_URI + "/" + member.getId())
              .content(asJsonString(userInput(member.getEmail(), organizationId))),
          organizationId);
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("A creation names B: the organization of B reaches the consumers of B only")
    void given_userCreationWithHeaderNamingB_should_streamTheOrganizationToTheConsumersOfB()
        throws Exception {
      // -- PREPARE --
      String organizationId = organizationOfB();
      String email = "user-fan-out-" + UUID.randomUUID() + "@filigran.io";
      entityManager.flush();
      entityManager.clear();
      TenantContext.clearCurrentTenant();

      // -- EXECUTE AND ASSERT --
      assertOrganizationOfBStreamedToTheConsumersOfBOnly(
          post(USER_URI).content(asJsonString(userInput(email, organizationId))), organizationId);
    }
  }
}
