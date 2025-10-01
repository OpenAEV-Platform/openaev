package io.openaev.rest.stream;

import static io.openaev.database.audit.ModelBaseListener.DATA_DELETE;
import static io.openaev.database.audit.ModelBaseListener.DATA_UPDATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.config.OpenAEVPrincipal;
import io.openaev.database.audit.BaseEvent;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.User;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.service.PermissionService;
import io.openaev.service.UserService;
import io.openaev.utils.fixtures.ScenarioFixture;
import java.lang.reflect.Field;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.FluxSink;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@ExtendWith(MockitoExtension.class)
public class StreamApiTest {

  private static final String RESOURCE_ID = "id";
  private static final String USER_ID = "userid";
  private static final String SESSION_ID = "sessionid";

  @Mock private User mockUser;

  @Mock private FluxSink<Object> mockSink;

  @Mock private PermissionService permissionService;

  @Mock private UserService userService;

  @Mock private ObjectMapper mapper;

  @InjectMocks private StreamApi streamApi;

  @BeforeEach
  public void setup() throws NoSuchFieldException, IllegalAccessException {
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
    Map<String, Tuple2<OpenAEVPrincipal, FluxSink<Object>>> consumers =
        (Map<String, Tuple2<OpenAEVPrincipal, FluxSink<Object>>>) consumersField.get(streamApi);
    consumers.put(SESSION_ID, Tuples.of(mockPrincipal, mockSink));
  }

  @Test
  public void test_listenDatabaseUpdate_WHEN_user_has_permission() {

    // mock PermissionService method
    when(permissionService.hasPermission(mockUser, RESOURCE_ID, ResourceType.SCENARIO, Action.READ))
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
    when(permissionService.hasPermission(mockUser, RESOURCE_ID, ResourceType.SCENARIO, Action.READ))
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
}
