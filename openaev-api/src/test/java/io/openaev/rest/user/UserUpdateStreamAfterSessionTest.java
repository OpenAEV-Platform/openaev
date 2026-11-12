package io.openaev.rest.user;

import static io.openaev.rest.user.TenantUserApi.USER_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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
import io.openaev.database.model.Tag;
import io.openaev.database.model.Team;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.TagRepository;
import io.openaev.database.repository.TeamRepository;
import io.openaev.service.PermissionService;
import io.openaev.service.StreamService;
import io.openaev.service.UserService;
import io.openaev.service.attackpath.AttackPathAccessControl;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.TagFixture;
import io.openaev.utils.fixtures.TeamFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.composers.UserComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.FluxSink;

/**
 * The stream serializes the instance an update event holds once per consumer, after the request and
 * its session are gone. So everything the stream reads from the user, the teams included, must be
 * loaded by the time the request ends.
 *
 * <p>The class is deliberately NOT {@code @Transactional}: the update commits and its session
 * closes before the events are delivered, as in production. A shared transaction keeps the session
 * open and hides a lazy collection that is not loaded.
 */
@RecordApplicationEvents
@WithMockUser(isAdmin = true)
@DisplayName("Stream delivery of a tenant user update after the session is closed")
public class UserUpdateStreamAfterSessionTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper mapper;
  @Autowired private ApplicationEvents applicationEvents;
  @Autowired private DataSource dataSource;
  @Autowired private TenantIsolationTestHelper tenantIsolationHelper;
  @Autowired private UserComposer userComposer;
  @Autowired private TagRepository tagRepository;
  @Autowired private TeamRepository teamRepository;
  @Autowired private UserService userService;
  @Autowired private AttackPathAccessControl attackPathAccessControl;

  private JdbcTemplate jdbc;
  private User member;
  private Tag tag;
  private Team team;

  @BeforeEach
  void memberHoldingATeamInTheDefaultTenant() {
    jdbc = new JdbcTemplate(dataSource);
    tenantIsolationHelper.attachCurrentUserToTenant(Tenant.DEFAULT_TENANT_UUID);
    member = userComposer.forUser(UserFixture.getUserWithDefaultEmail()).persist().get();
    tenantIsolationHelper.attachUserToTenant(member.getId(), Tenant.DEFAULT_TENANT_UUID);
    tag = TagFixture.getTagWithText("after-session-" + UUID.randomUUID());
    tag.setTenant(new Tenant(Tenant.DEFAULT_TENANT_UUID));
    tag = tagRepository.save(tag);
    team = TeamFixture.createTeamWithName("after-session-" + UUID.randomUUID());
    team.setTenant(new Tenant(Tenant.DEFAULT_TENANT_UUID));
    team = teamRepository.save(team);
    jdbc.update(
        "INSERT INTO users_teams (user_id, team_id) VALUES (?, ?)", member.getId(), team.getId());
    TenantContext.clearCurrentTenant();
  }

  @AfterEach
  void cleanup() {
    TenantContext.clearCurrentTenant();
    jdbc.update("DELETE FROM users_teams WHERE user_id = ?", member.getId());
    jdbc.update("DELETE FROM users_tags WHERE user_id = ?", member.getId());
    jdbc.update("DELETE FROM users_tenants WHERE user_id = ?", member.getId());
    jdbc.update("DELETE FROM users_groups WHERE user_id = ?", member.getId());
    jdbc.update("DELETE FROM users WHERE user_id = ?", member.getId());
    jdbc.update("DELETE FROM tags WHERE tag_id = ?", tag.getId());
    jdbc.update("DELETE FROM teams WHERE team_id = ?", team.getId());
  }

  @SuppressWarnings("unchecked")
  private FluxSink<Object> consumerOfDefaultTenant(StreamService stream) throws Exception {
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
        .put("default", constructor.newInstance(principal, Tenant.DEFAULT_TENANT_UUID, sink));
    return sink;
  }

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

  private List<String> idsOf(JsonNode array) {
    return mapper.convertValue(
        array, mapper.getTypeFactory().constructCollectionType(List.class, String.class));
  }

  @Test
  @DisplayName("Tags changed with no organization change: the update reaches the stream")
  void given_tagsChangedAndNoOrganization_should_deliverTheUpdateAfterTheSessionCloses()
      throws Exception {
    // -- PREPARE --
    PermissionService permissions = mock(PermissionService.class);
    when(permissions.hasPermissionInTenant(any(), any(), any(), any(), any())).thenReturn(true);
    StreamService stream =
        new StreamService(permissions, userService, attackPathAccessControl, mapper);
    FluxSink<Object> sink = consumerOfDefaultTenant(stream);
    UserInput input =
        new UserInput(
            member.getEmail(),
            "Changed-" + UUID.randomUUID(),
            "User",
            null,
            null,
            null,
            null,
            null,
            List.of(tag.getId()),
            false,
            List.of());
    long publishedBefore = applicationEvents.stream(BaseEvent.class).count();

    // -- EXECUTE --
    mvc.perform(
            put(USER_URI + "/" + member.getId())
                .content(asJsonString(input))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful());
    List<BaseEvent> events =
        applicationEvents.stream(BaseEvent.class)
            .skip(publishedBefore)
            .filter(
                event ->
                    event.getInstance() instanceof User user && member.getId().equals(user.getId()))
            .toList();
    assertFalse(events.isEmpty(), "The update must have published an event for the user");
    events.forEach(stream::listenDatabaseUpdate);

    // -- ASSERT --
    List<JsonNode> received = receivedBy(sink);
    assertFalse(received.isEmpty(), "The consumer must receive the update of the user");
    received.forEach(
        instance -> {
          assertEquals(List.of(tag.getId()), idsOf(instance.path("user_tags")));
          assertEquals(List.of(team.getId()), idsOf(instance.path("user_teams")));
        });
  }
}
