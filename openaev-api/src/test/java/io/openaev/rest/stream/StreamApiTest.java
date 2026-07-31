package io.openaev.rest.stream;

import static io.openaev.database.audit.ModelBaseListener.DATA_DELETE;
import static io.openaev.database.audit.ModelBaseListener.DATA_UPDATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.config.OpenAEVPrincipal;
import io.openaev.database.audit.BaseEvent;
import io.openaev.database.model.*;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.service.PermissionService;
import io.openaev.service.UserService;
import io.openaev.service.attackpath.AttackPathAccessControl;
import io.openaev.service.attackpath.ingestion.AttackPathVersionEvent;
import io.openaev.utils.fixtures.ScenarioFixture;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.FluxSink;

@MockitoSettings(strictness = Strictness.LENIENT) // class-wide
@ExtendWith(MockitoExtension.class)
public class StreamApiTest {

  private static final String RESOURCE_ID = "id";
  private static final String USER_ID = "userid";
  private static final String SESSION_ID = "sessionid";
  private static final String TENANT_ID = "tenant-a";
  private static final String OTHER_TENANT_ID = "tenant-b";

  @Mock private User mockUser;

  @Mock private FluxSink<Object> mockSink;

  @Mock private PermissionService permissionService;

  @Mock private UserService userService;

  @Mock private ObjectMapper mapper;

  @Mock private AttackPathAccessControl attackPathAccessControl;

  @InjectMocks private StreamApi streamApi;

  @BeforeEach
  public void setup() throws Exception {
    // mock consumer
    OpenAEVPrincipal mockPrincipal = mock(OpenAEVPrincipal.class);
    when(mockPrincipal.getId()).thenReturn(USER_ID);
    when(userService.user(USER_ID)).thenReturn(mockUser);

    // mock objectmapper using reflection
    Field mapperField = RestBehavior.class.getDeclaredField("mapper");
    mapperField.setAccessible(true);
    mapperField.set(streamApi, mapper);

    // inject into consumers using reflection
    Field consumersField = StreamApi.class.getDeclaredField("consumers");
    consumersField.setAccessible(true);
    @SuppressWarnings("unchecked")
    Map<String, Object> consumers = (Map<String, Object>) consumersField.get(streamApi);
    consumers.put(SESSION_ID, buildStreamConsumer(mockPrincipal, null, mockSink));
  }

  private static Object buildStreamConsumer(
      OpenAEVPrincipal principal, String tenantId, FluxSink<Object> sink) throws Exception {
    Class<?> streamConsumerClass = Class.forName("io.openaev.rest.stream.StreamApi$StreamConsumer");
    RecordComponent[] components = streamConsumerClass.getRecordComponents();
    Class<?>[] parameterTypes =
        new Class<?>[] {
          components[0].getType(), components[1].getType(), components[2].getType(),
        };
    Constructor<?> constructor = streamConsumerClass.getDeclaredConstructor(parameterTypes);
    constructor.setAccessible(true);
    return constructor.newInstance(principal, tenantId, sink);
  }

  private boolean invokeIsVisibleForTenant(BaseEvent event, String tenantId) throws Exception {
    Method method =
        StreamApi.class.getDeclaredMethod("isVisibleForTenant", BaseEvent.class, String.class);
    method.setAccessible(true);
    return (boolean) method.invoke(streamApi, event, tenantId);
  }

  private static Tenant tenant(String id) {
    Tenant tenant = new Tenant();
    tenant.setId(id);
    return tenant;
  }

  @Test
  public void test_listenDatabaseUpdate_WHEN_user_has_permission() {

    // mock PermissionService method
    when(permissionService.hasPermission(
            mockUser, Optional.empty(), RESOURCE_ID, ResourceType.SCENARIO, Action.READ))
        .thenReturn(true);

    Scenario scenario = ScenarioFixture.getScenario();
    scenario.setId(RESOURCE_ID);
    BaseEvent event = new BaseEvent(DATA_UPDATE, scenario, mock(ObjectMapper.class));

    // call the method
    streamApi.listenDatabaseUpdate(event);

    // capture the event and verify data
    ArgumentCaptor<ServerSentEvent> captor = ArgumentCaptor.forClass(ServerSentEvent.class);
    verify(mockSink).next(captor.capture());

    ServerSentEvent<?> serverSentEvent = captor.getValue();
    BaseEvent baseEventCaptured = (BaseEvent) serverSentEvent.data();
    assertEquals(event.getType(), baseEventCaptured.getType());
    assertTrue(baseEventCaptured.getInstance() instanceof Scenario);
    assertEquals(scenario.getId(), ((Scenario) baseEventCaptured.getInstance()).getId());
  }

  @Test
  public void test_listenDatabaseUpdate_WHEN_user_has_not_permission() {

    when(mapper.createObjectNode()).thenReturn(mock(ObjectNode.class));

    // mock PermissionService method
    when(permissionService.hasPermission(
            mockUser, Optional.empty(), RESOURCE_ID, ResourceType.SCENARIO, Action.READ))
        .thenReturn(false);

    Scenario scenario = ScenarioFixture.getScenario();
    scenario.setId(RESOURCE_ID);
    BaseEvent event = new BaseEvent(DATA_UPDATE, scenario, mock(ObjectMapper.class));

    // call the method
    streamApi.listenDatabaseUpdate(event);

    // capture the event and verify data
    ArgumentCaptor<ServerSentEvent> captor = ArgumentCaptor.forClass(ServerSentEvent.class);
    verify(mockSink).next(captor.capture());

    ServerSentEvent<?> serverSentEvent = captor.getValue();
    BaseEvent baseEventCaptured = (BaseEvent) serverSentEvent.data();
    assertEquals(DATA_DELETE, baseEventCaptured.getType());
    assertTrue(baseEventCaptured.getInstance() instanceof Scenario);
    assertEquals(scenario.getId(), ((Scenario) baseEventCaptured.getInstance()).getId());
  }

  @Test
  public void test_listenDatabaseUpdate_WHEN_same_resource_should_resolve_permission_once() {
    // The broadcast path used to resolve permissions per event per consumer, flooding the
    // database while viewing a running simulation (#6868): repeated events on the same
    // resource must be served from the decision cache.
    when(permissionService.hasPermission(
            mockUser, Optional.empty(), RESOURCE_ID, ResourceType.SCENARIO, Action.READ))
        .thenReturn(true);

    Scenario scenario = ScenarioFixture.getScenario();
    scenario.setId(RESOURCE_ID);

    streamApi.listenDatabaseUpdate(new BaseEvent(DATA_UPDATE, scenario, mock(ObjectMapper.class)));
    streamApi.listenDatabaseUpdate(new BaseEvent(DATA_UPDATE, scenario, mock(ObjectMapper.class)));

    verify(permissionService, times(1))
        .hasPermission(mockUser, Optional.empty(), RESOURCE_ID, ResourceType.SCENARIO, Action.READ);
    verify(mockSink, times(2)).next(any());
  }

  @Test
  public void test_given_databaseEvent_when_eventIsCVE_then_doNothing() {
    Vulnerability vulnerability = new Vulnerability();
    BaseEvent event = new BaseEvent(DATA_UPDATE, vulnerability, mock(ObjectMapper.class));

    streamApi.listenDatabaseUpdate(event);

    verify(mockSink, never()).next(any());
  }

  @Test
  public void given_tenantScopedEvent_when_tenantMatches_should_beVisible() throws Exception {
    // Arrange
    Scenario scenario = ScenarioFixture.getScenario();
    scenario.setTenant(tenant(TENANT_ID));
    BaseEvent event = new BaseEvent(DATA_UPDATE, scenario, mock(ObjectMapper.class));

    // Act
    boolean visible = invokeIsVisibleForTenant(event, TENANT_ID);

    // Assert
    assertTrue(visible);
  }

  @Test
  public void given_tenantScopedEvent_when_tenantDiffers_should_notBeVisible() throws Exception {
    // Arrange
    Scenario scenario = ScenarioFixture.getScenario();
    scenario.setTenant(tenant(TENANT_ID));
    BaseEvent event = new BaseEvent(DATA_UPDATE, scenario, mock(ObjectMapper.class));

    // Act
    boolean visible = invokeIsVisibleForTenant(event, OTHER_TENANT_ID);

    // Assert
    assertFalse(visible);
  }

  @Test
  public void given_dualScopeEventWithoutTenant_when_consumerHasTenant_should_notBeVisible()
      throws Exception {
    // Arrange
    Setting setting = new Setting();
    setting.setId("setting-id");
    BaseEvent event = new BaseEvent(DATA_UPDATE, setting, mock(ObjectMapper.class));

    // Act
    boolean visible = invokeIsVisibleForTenant(event, TENANT_ID);

    // Assert
    assertFalse(visible);
  }

  @Test
  public void given_tenantScopedEvent_when_consumerHasNoTenant_should_beVisible() throws Exception {
    // Arrange
    Scenario scenario = ScenarioFixture.getScenario();
    scenario.setTenant(tenant(TENANT_ID));
    BaseEvent event = new BaseEvent(DATA_UPDATE, scenario, mock(ObjectMapper.class));

    // Act
    boolean visible = invokeIsVisibleForTenant(event, null);

    // Assert
    assertTrue(visible);
  }

  // -- Attack-path version nudge (#6647, spec 003) --
  //
  // The nudge announces that a simulation's attack-path version moved. It carries no graph data, so
  // what these tests pin is who receives it: the audience of the delta read it announces, in the
  // owning tenant, and nobody else.

  private static final String SIMULATION_ID = "simulation-1";
  private static final String SEED_SIMULATION_ID = "ap-seed-demo";

  /** Replaces the default (tenant-less) consumer with one scoped to the given tenant. */
  private FluxSink<Object> registerTenantConsumer(String tenantId) throws Exception {
    OpenAEVPrincipal principal = mock(OpenAEVPrincipal.class);
    when(principal.getId()).thenReturn(USER_ID);
    FluxSink<Object> sink = mock(FluxSink.class);
    Field consumersField = StreamApi.class.getDeclaredField("consumers");
    consumersField.setAccessible(true);
    @SuppressWarnings("unchecked")
    Map<String, Object> consumers = (Map<String, Object>) consumersField.get(streamApi);
    consumers.clear();
    consumers.put(SESSION_ID, buildStreamConsumer(principal, tenantId, sink));
    return sink;
  }

  @Test
  public void
      given_attackPathNudge_when_consumerCanReadTheSimulation_should_receiveTheNotificationOnly()
          throws Exception {
    // Arrange: a non-admin consumer whose only right is READ on this simulation — the case that
    // proves the gate consults grants instead of falling back to "admins only".
    FluxSink<Object> sink = registerTenantConsumer(TENANT_ID);
    when(attackPathAccessControl.canRead(mockUser, SIMULATION_ID)).thenReturn(true);

    // Act
    streamApi.listenAttackPathVersion(new AttackPathVersionEvent(SIMULATION_ID, TENANT_ID, 42L));

    // Assert: one event, of the attack-path type, carrying the notification and nothing else.
    ArgumentCaptor<ServerSentEvent> captor = ArgumentCaptor.forClass(ServerSentEvent.class);
    verify(sink).next(captor.capture());
    ServerSentEvent<?> sent = captor.getValue();
    assertEquals(StreamApi.EVENT_TYPE_ATTACK_PATH_VERSION, sent.event());
    AttackPathVersionEvent payload = (AttackPathVersionEvent) sent.data();
    assertEquals(SIMULATION_ID, payload.simulationId());
    assertEquals(42L, payload.version());

    // And on the wire: exactly the two notification fields. Asserted on the serialized form, not on
    // the record's accessors, because the invariant that matters is that the routing tenant never
    // leaves the server — a check the accessors cannot make.
    JsonNode wire = new ObjectMapper().valueToTree(payload);
    assertEquals(
        Set.of("simulation_id", "version"),
        wire.properties().stream().map(Map.Entry::getKey).collect(Collectors.toSet()));
    assertEquals(SIMULATION_ID, wire.get("simulation_id").asText());
    assertEquals(42L, wire.get("version").asLong());
  }

  @Test
  public void given_attackPathNudge_when_consumerCannotReadTheSimulation_should_receiveNothing()
      throws Exception {
    // Arrange: no READ on the simulation. The point is silence — never the DATA_DELETE masking
    // event the generic path sends, which would evict the simulation from the client's store.
    FluxSink<Object> sink = registerTenantConsumer(TENANT_ID);
    when(attackPathAccessControl.canRead(mockUser, SIMULATION_ID)).thenReturn(false);

    // Act
    streamApi.listenAttackPathVersion(new AttackPathVersionEvent(SIMULATION_ID, TENANT_ID, 7L));

    // Assert
    verify(sink, never()).next(any());
  }

  @Test
  public void given_attackPathNudge_when_consumerIsInAnotherTenant_should_receiveNothing()
      throws Exception {
    // Arrange: authorized on paper, but connected under another tenant.
    FluxSink<Object> sink = registerTenantConsumer(OTHER_TENANT_ID);
    when(attackPathAccessControl.canRead(mockUser, SIMULATION_ID)).thenReturn(true);

    // Act
    streamApi.listenAttackPathVersion(new AttackPathVersionEvent(SIMULATION_ID, TENANT_ID, 3L));

    // Assert: tenant equality runs before the permission check, so nothing is delivered — and the
    // check is never even consulted.
    verify(sink, never()).next(any());
    verify(attackPathAccessControl, never()).canRead(any(), any());
  }

  @Test
  public void given_attackPathNudge_when_eventCarriesNoTenant_should_receiveNothing()
      throws Exception {
    // Arrange: a routing tenant we cannot match must fail closed, not broadcast to everyone.
    FluxSink<Object> sink = registerTenantConsumer(TENANT_ID);

    // Act
    streamApi.listenAttackPathVersion(new AttackPathVersionEvent(SIMULATION_ID, null, 1L));

    // Assert
    verify(sink, never()).next(any());
  }

  @Test
  public void given_attackPathNudge_when_simulationIsSeeded_should_beDelivered() throws Exception {
    // Arrange: a seeded simulation is not a real exercise, so a bare grant check would refuse it
    // while the delta read serves it — the nudge must follow the read, hence the shared predicate.
    FluxSink<Object> sink = registerTenantConsumer(TENANT_ID);
    when(attackPathAccessControl.canRead(mockUser, SEED_SIMULATION_ID)).thenReturn(true);

    // Act
    streamApi.listenAttackPathVersion(
        new AttackPathVersionEvent(SEED_SIMULATION_ID, TENANT_ID, 5L));

    // Assert
    verify(sink).next(any());
  }
}
