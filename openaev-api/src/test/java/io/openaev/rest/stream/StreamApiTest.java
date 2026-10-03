package io.openaev.rest.stream;

import static io.openaev.database.audit.ModelBaseListener.DATA_DELETE;
import static io.openaev.database.audit.ModelBaseListener.DATA_PERSIST;
import static io.openaev.database.audit.ModelBaseListener.DATA_UPDATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.config.OpenAEVPrincipal;
import io.openaev.context.TenantContext;
import io.openaev.database.audit.BaseEvent;
import io.openaev.database.model.*;
import io.openaev.helper.ObjectMapperHelper;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.service.PermissionService;
import io.openaev.service.UserService;
import io.openaev.service.attackpath.AttackPathAccessControl;
import io.openaev.service.attackpath.ingestion.AttackPathVersionEvent;
import io.openaev.service.utils.BulkOperationMonitor.BulkOperation;
import io.openaev.service.utils.BulkOperationMonitor.BulkOperationEvent;
import io.openaev.service.utils.BulkOperationMonitor.BulkOperationStatus;
import io.openaev.utils.fixtures.ScenarioFixture;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.FluxSink;

@MockitoSettings(strictness = Strictness.LENIENT) // class-wide
@ExtendWith(MockitoExtension.class)
public class StreamApiTest {

  private static final String RESOURCE_ID = "id";
  private static final String USER_ID = "userid";
  private static final String SESSION_ID = "sessionid";
  private static final String TENANT_ID = "tenant-a";
  private static final String OTHER_TENANT_ID = "tenant-b";
  private static final String OTHER_USER_ID = "other-user";
  private static final String OTHER_SESSION_ID = "other-session";

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

  private ObjectMapper useRealMapper() {
    ObjectMapper jsonMapper = ObjectMapperHelper.openAEVJsonMapper();
    ReflectionTestUtils.setField(streamApi, "mapper", jsonMapper);
    return jsonMapper;
  }

  private JsonNode captureWireEvent(FluxSink<Object> sink, ObjectMapper jsonMapper) {
    ArgumentCaptor<ServerSentEvent> captor = ArgumentCaptor.forClass(ServerSentEvent.class);
    verify(sink).next(captor.capture());
    assertEquals(StreamApi.EVENT_TYPE_MESSAGE, captor.getValue().event());
    return jsonMapper.valueToTree(captor.getValue().data());
  }

  private void denyRead(Base resource) {
    stubRead(mockUser, resource, false);
  }

  private void stubRead(User user, Base resource, boolean allowed) {
    when(permissionService.hasPermission(
            user, Optional.empty(), RESOURCE_ID, resource.getResourceType(), Action.READ))
        .thenReturn(allowed);
  }

  private void failRead(User user, Base resource) {
    when(permissionService.hasPermission(
            user, Optional.empty(), RESOURCE_ID, resource.getResourceType(), Action.READ))
        .thenThrow(new ElementNotFoundException("Not found with id: " + RESOURCE_ID));
  }

  /** Adds a second tenant-less consumer next to the default one and returns its sink. */
  private FluxSink<Object> registerSecondConsumer(User user) throws Exception {
    OpenAEVPrincipal principal = mock(OpenAEVPrincipal.class);
    when(principal.getId()).thenReturn(OTHER_USER_ID);
    when(userService.user(OTHER_USER_ID)).thenReturn(user);
    FluxSink<Object> sink = mock(FluxSink.class);
    @SuppressWarnings("unchecked")
    Map<String, Object> consumers =
        (Map<String, Object>) ReflectionTestUtils.getField(streamApi, "consumers");
    consumers.put(OTHER_SESSION_ID, buildStreamConsumer(principal, null, sink));
    return sink;
  }

  private void assertDeniedDeletionIsMasked(Base resource, String idProperty) {
    ObjectMapper jsonMapper = useRealMapper();
    denyRead(resource);

    BaseEvent event = new BaseEvent(DATA_DELETE, resource, jsonMapper);
    JsonNode originalData = event.getInstanceData().deepCopy();
    streamApi.listenDatabaseUpdate(event);

    JsonNode wire = captureWireEvent(mockSink, jsonMapper);
    ObjectNode expectedInstance = jsonMapper.createObjectNode().put(idProperty, RESOURCE_ID);
    assertEquals(DATA_DELETE, wire.path("event_type").asText());
    assertEquals(idProperty, wire.path("attribute_id").asText());
    assertEquals(expectedInstance, wire.path("instance"));
    assertEquals(originalData, event.getInstanceData());
  }

  private void assertDeniedMutationIsDropped(Base resource, String eventType) {
    ObjectMapper jsonMapper = useRealMapper();
    denyRead(resource);

    BaseEvent event = new BaseEvent(eventType, resource, jsonMapper);
    JsonNode originalData = event.getInstanceData().deepCopy();
    streamApi.listenDatabaseUpdate(event);

    verify(mockSink, never()).next(any());
    assertEquals(eventType, event.getType());
    assertEquals(originalData, event.getInstanceData());
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

  private static Agent restrictedAgent() {
    Agent agent = new Agent();
    agent.setId(RESOURCE_ID);
    agent.setVersion("restricted-agent-version");
    return agent;
  }

  private static Endpoint restrictedEndpoint() {
    Endpoint endpoint = new Endpoint();
    endpoint.setId(RESOURCE_ID);
    endpoint.setName("restricted-endpoint");
    return endpoint;
  }

  private static PreventionInjectExpectation restrictedExpectation() {
    PreventionInjectExpectation expectation = new PreventionInjectExpectation();
    expectation.setId(RESOURCE_ID);
    return expectation;
  }

  @ParameterizedTest
  @ValueSource(strings = {DATA_PERSIST, DATA_UPDATE})
  public void given_agentMutation_when_userCannotRead_should_notReceiveIt(String eventType) {
    assertDeniedMutationIsDropped(restrictedAgent(), eventType);
  }

  @Test
  public void given_agentDeletion_when_userCannotRead_should_receiveAnIdOnlyTombstone() {
    assertDeniedDeletionIsMasked(restrictedAgent(), "agent_id");
  }

  @Test
  public void given_assetDeletion_when_userCannotRead_should_receiveAnIdOnlyTombstone() {
    Asset asset = new Asset();
    asset.setId(RESOURCE_ID);
    assertDeniedDeletionIsMasked(asset, "asset_id");
  }

  @ParameterizedTest
  @ValueSource(strings = {DATA_PERSIST, DATA_UPDATE})
  public void given_endpointMutation_when_userCannotRead_should_notReceiveIt(String eventType) {
    assertDeniedMutationIsDropped(restrictedEndpoint(), eventType);
  }

  @Test
  public void given_endpointDeletion_when_userCannotRead_should_maskTheInheritedId() {
    assertDeniedDeletionIsMasked(restrictedEndpoint(), "asset_id");
  }

  @ParameterizedTest
  @ValueSource(strings = {DATA_PERSIST, DATA_UPDATE})
  public void given_expectationMutation_when_userCannotRead_should_notReceiveIt(String eventType) {
    assertDeniedMutationIsDropped(restrictedExpectation(), eventType);
  }

  @Test
  public void given_expectationDeletion_when_userCannotRead_should_maskTheInheritedId() {
    assertDeniedDeletionIsMasked(restrictedExpectation(), "inject_expectation_id");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  "})
  public void given_deniedDeletion_when_eventHasNoIdAttribute_should_sendNothing(
      String attributeId) {
    ObjectMapper jsonMapper = useRealMapper();
    Agent agent = restrictedAgent();
    denyRead(agent);
    BaseEvent event = new BaseEvent(DATA_DELETE, agent, jsonMapper);
    event.setAttributeId(attributeId);

    streamApi.listenDatabaseUpdate(event);

    verify(mockSink, never()).next(any());
  }

  // -- Fan-out isolation --
  //
  // Each consumer's outcome is computed on its own: a denied, failing or masked consumer must
  // neither alter what the others receive nor stop delivery to them. Every test swaps which of the
  // two consumers is the odd one out, so both iteration orders of the consumer map are covered.

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void given_deletion_when_oneConsumerCannotRead_should_maskOnlyThatConsumer(
      boolean defaultConsumerDenied) throws Exception {
    ObjectMapper jsonMapper = useRealMapper();
    User otherUser = mock(User.class);
    FluxSink<Object> otherSink = registerSecondConsumer(otherUser);
    Agent agent = restrictedAgent();
    stubRead(mockUser, agent, !defaultConsumerDenied);
    stubRead(otherUser, agent, defaultConsumerDenied);
    FluxSink<Object> deniedSink = defaultConsumerDenied ? mockSink : otherSink;
    FluxSink<Object> allowedSink = defaultConsumerDenied ? otherSink : mockSink;

    BaseEvent event = new BaseEvent(DATA_DELETE, agent, jsonMapper);
    JsonNode originalData = event.getInstanceData().deepCopy();
    streamApi.listenDatabaseUpdate(event);

    assertEquals(
        jsonMapper.createObjectNode().put("agent_id", RESOURCE_ID),
        captureWireEvent(deniedSink, jsonMapper).path("instance"));
    assertEquals(originalData, captureWireEvent(allowedSink, jsonMapper).path("instance"));
    assertEquals(originalData, event.getInstanceData());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void given_deletion_when_permissionCheckThrows_should_sendTombstoneAndServeOthers(
      boolean defaultConsumerFails) throws Exception {
    // A deleted inject (or objective, evaluation, workflow...) can no longer be resolved to its
    // parent, so the check throws for every non-admin consumer: the deletion must still reach them.
    ObjectMapper jsonMapper = useRealMapper();
    User otherUser = mock(User.class);
    FluxSink<Object> otherSink = registerSecondConsumer(otherUser);
    Agent agent = restrictedAgent();
    failRead(defaultConsumerFails ? mockUser : otherUser, agent);
    stubRead(defaultConsumerFails ? otherUser : mockUser, agent, true);
    FluxSink<Object> failingSink = defaultConsumerFails ? mockSink : otherSink;
    FluxSink<Object> healthySink = defaultConsumerFails ? otherSink : mockSink;

    BaseEvent event = new BaseEvent(DATA_DELETE, agent, jsonMapper);
    JsonNode originalData = event.getInstanceData().deepCopy();
    streamApi.listenDatabaseUpdate(event);

    assertEquals(
        jsonMapper.createObjectNode().put("agent_id", RESOURCE_ID),
        captureWireEvent(failingSink, jsonMapper).path("instance"));
    assertEquals(originalData, captureWireEvent(healthySink, jsonMapper).path("instance"));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void given_update_when_permissionCheckThrows_should_dropItAndServeOthers(
      boolean defaultConsumerFails) throws Exception {
    ObjectMapper jsonMapper = useRealMapper();
    User otherUser = mock(User.class);
    FluxSink<Object> otherSink = registerSecondConsumer(otherUser);
    Agent agent = restrictedAgent();
    failRead(defaultConsumerFails ? mockUser : otherUser, agent);
    stubRead(defaultConsumerFails ? otherUser : mockUser, agent, true);
    FluxSink<Object> failingSink = defaultConsumerFails ? mockSink : otherSink;
    FluxSink<Object> healthySink = defaultConsumerFails ? otherSink : mockSink;

    streamApi.listenDatabaseUpdate(new BaseEvent(DATA_UPDATE, agent, jsonMapper));

    verify(failingSink, never()).next(any());
    verify(healthySink).next(any());
  }

  @Test
  public void given_permissionCheckThrows_should_notCacheTheDenial() {
    ObjectMapper jsonMapper = useRealMapper();
    Agent agent = restrictedAgent();
    when(permissionService.hasPermission(
            mockUser, Optional.empty(), RESOURCE_ID, agent.getResourceType(), Action.READ))
        .thenThrow(new ElementNotFoundException("transient"))
        .thenReturn(true);

    streamApi.listenDatabaseUpdate(new BaseEvent(DATA_UPDATE, agent, jsonMapper));
    streamApi.listenDatabaseUpdate(new BaseEvent(DATA_UPDATE, agent, jsonMapper));

    verify(permissionService, times(2))
        .hasPermission(
            mockUser, Optional.empty(), RESOURCE_ID, agent.getResourceType(), Action.READ);
    verify(mockSink, times(1)).next(any());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void given_oneConsumerFails_should_keepServingTheOthers(boolean defaultConsumerFails)
      throws Exception {
    ObjectMapper jsonMapper = useRealMapper();
    User otherUser = mock(User.class);
    FluxSink<Object> otherSink = registerSecondConsumer(otherUser);
    Agent agent = restrictedAgent();
    stubRead(mockUser, agent, true);
    stubRead(otherUser, agent, true);
    when(userService.user(defaultConsumerFails ? USER_ID : OTHER_USER_ID))
        .thenThrow(new ElementNotFoundException("User not found"));
    FluxSink<Object> failingSink = defaultConsumerFails ? mockSink : otherSink;
    FluxSink<Object> healthySink = defaultConsumerFails ? otherSink : mockSink;

    streamApi.listenDatabaseUpdate(new BaseEvent(DATA_UPDATE, agent, jsonMapper));

    verify(failingSink, never()).next(any());
    verify(healthySink).next(any());
  }

  @ParameterizedTest
  @ValueSource(strings = {DATA_PERSIST, DATA_UPDATE, DATA_DELETE})
  public void given_notifierEvent_should_maskConfigurationPerConsumer(String eventType)
      throws Exception {
    ObjectMapper jsonMapper = useRealMapper();
    FluxSink<Object> restrictedSink = registerTenantConsumer(TENANT_ID);
    User privilegedUser = mock(User.class);
    OpenAEVPrincipal privilegedPrincipal = mock(OpenAEVPrincipal.class);
    when(privilegedPrincipal.getId()).thenReturn("privileged-user");
    when(userService.user("privileged-user")).thenReturn(privilegedUser);
    FluxSink<Object> privilegedSink = mock(FluxSink.class);
    @SuppressWarnings("unchecked")
    Map<String, Object> consumers =
        (Map<String, Object>) ReflectionTestUtils.getField(streamApi, "consumers");
    consumers.put(
        "privileged-session", buildStreamConsumer(privilegedPrincipal, TENANT_ID, privilegedSink));

    when(permissionService.hasPermission(
            mockUser, Optional.empty(), RESOURCE_ID, ResourceType.NOTIFIER, Action.READ))
        .thenReturn(true);
    when(permissionService.hasPermission(
            privilegedUser, Optional.empty(), RESOURCE_ID, ResourceType.NOTIFIER, Action.READ))
        .thenReturn(true);
    when(permissionService.hasCapabilityPermission(mockUser, ResourceType.NOTIFIER, Action.READ))
        .thenAnswer(
            invocation -> {
              assertEquals(TENANT_ID, TenantContext.getCurrentTenant());
              return false;
            });
    when(permissionService.hasCapabilityPermission(
            privilegedUser, ResourceType.NOTIFIER, Action.READ))
        .thenAnswer(
            invocation -> {
              assertEquals(TENANT_ID, TenantContext.getCurrentTenant());
              return true;
            });

    Map<String, Object> configuration =
        Map.of("headers", Map.of("Authorization", "synthetic-stream-secret"));
    Notifier notifier = new Notifier();
    notifier.setId(RESOURCE_ID);
    notifier.setTenant(tenant(TENANT_ID));
    notifier.setConfiguration(configuration);
    BaseEvent event = new BaseEvent(eventType, notifier, jsonMapper);
    JsonNode originalData = event.getInstanceData().deepCopy();

    streamApi.listenDatabaseUpdate(event);

    JsonNode restrictedWire = captureWireEvent(restrictedSink, jsonMapper);
    JsonNode privilegedWire = captureWireEvent(privilegedSink, jsonMapper);
    assertEquals(eventType, restrictedWire.path("event_type").asText());
    assertEquals(eventType, privilegedWire.path("event_type").asText());
    assertEquals(RESOURCE_ID, restrictedWire.path("instance").path("notifier_id").asText());
    assertTrue(restrictedWire.path("instance").path("notifier_configuration").isNull());
    assertEquals(originalData, privilegedWire.path("instance"));
    assertEquals(originalData, event.getInstanceData());
    assertEquals(configuration, notifier.getConfiguration());
    verify(permissionService).hasCapabilityPermission(mockUser, ResourceType.NOTIFIER, Action.READ);
    verify(permissionService)
        .hasCapabilityPermission(privilegedUser, ResourceType.NOTIFIER, Action.READ);
  }

  @Test
  public void given_notificationEvent_when_consumerDoesNotOwnIt_should_notReceiveIt() {
    User owner = mock(User.class);
    when(owner.getId()).thenReturn("other-user");
    Notification notification = new Notification();
    notification.setId(RESOURCE_ID);
    notification.setUser(owner);

    streamApi.listenDatabaseUpdate(
        new BaseEvent(DATA_UPDATE, notification, mock(ObjectMapper.class)));

    verify(mockSink, never()).next(any());
  }

  @Test
  public void given_bulkOperation_when_consumerIsNotOwner_should_notReceiveIt() {
    streamApi.listenBulkOperation(new BulkOperationEvent(bulkOperation(TENANT_ID, "other-user")));

    verify(mockSink, never()).next(any());
  }

  @Test
  public void given_bulkOperation_when_consumerIsInAnotherTenant_should_notReceiveIt()
      throws Exception {
    FluxSink<Object> sink = registerTenantConsumer(OTHER_TENANT_ID);

    streamApi.listenBulkOperation(new BulkOperationEvent(bulkOperation(TENANT_ID, USER_ID)));

    verify(sink, never()).next(any());
  }

  private static BulkOperation bulkOperation(String tenantId, String userId) {
    return new BulkOperation(
        "bulk-operation-id",
        "delete",
        "assets",
        2,
        1,
        BulkOperationStatus.RUNNING,
        Instant.EPOCH,
        null,
        tenantId,
        userId);
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
