package io.openbas.rest.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openbas.config.OpenBASPrincipal;
import io.openbas.database.audit.BaseEvent;
import io.openbas.database.model.Action;
import io.openbas.database.model.Payload;
import io.openbas.database.model.ResourceType;
import io.openbas.database.model.Scenario;
import io.openbas.database.model.User;
import io.openbas.rest.helper.RestBehavior;
import io.openbas.service.PermissionService;
import io.openbas.service.UserService;
import io.openbas.utils.fixtures.ScenarioFixture;
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

import java.lang.reflect.Field;
import java.util.Map;

import static io.openbas.database.audit.ModelBaseListener.DATA_DELETE;
import static io.openbas.database.audit.ModelBaseListener.DATA_UPDATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class StreamApiTest {

    private static final String RESOURCE_ID = "id";
    private static final String USER_ID = "userid";
    private static final String SESSION_ID = "sessionid";

    @Mock
    private User mockUser;

    @Mock
    private FluxSink<Object> mockSink;

    @Mock
    private PermissionService permissionService;

    @Mock
    private UserService userService;

    @Mock
    private ObjectMapper mapper;

    @InjectMocks
    private StreamApi streamApi;


    @BeforeEach
    public void setup() throws NoSuchFieldException, IllegalAccessException {
        //mock consumer
        OpenBASPrincipal mockPrincipal = mock(OpenBASPrincipal.class);
        when(mockPrincipal.getId()).thenReturn(USER_ID);
        when(userService.user(USER_ID)).thenReturn(mockUser);

        //mock objectmapper using reflection
        Field mapperField = RestBehavior.class.getDeclaredField("mapper");
        mapperField.setAccessible(true);
        mapperField.set(streamApi, mapper);

        // inject into consumers using reflection
        Field consumersField = StreamApi.class.getDeclaredField("consumers");
        consumersField.setAccessible(true);
        Map<String, Tuple2<OpenBASPrincipal, FluxSink<Object>>> consumers =
                (Map<String, Tuple2<OpenBASPrincipal, FluxSink<Object>>>) consumersField.get(streamApi);
        consumers.put(SESSION_ID, Tuples.of(mockPrincipal, mockSink));
    }
    @Test
    public void test_listenDatabaseUpdate_WHEN_user_has_permission() {

        //mock PermissionService method
        when(permissionService.hasPermission(mockUser,RESOURCE_ID, ResourceType.SCENARIO, Action.READ)).thenReturn(true);

        Scenario scenario =  ScenarioFixture.getScenario();
        scenario.setId(RESOURCE_ID);
        BaseEvent event = new BaseEvent(DATA_UPDATE, scenario, mock(ObjectMapper.class));


        //call the method
        streamApi.listenDatabaseUpdate(event);

        //capture the event and verify data
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

        //mock PermissionService method
        when(permissionService.hasPermission(mockUser,RESOURCE_ID, ResourceType.SCENARIO, Action.READ)).thenReturn(false);

        Scenario scenario =  ScenarioFixture.getScenario();
        scenario.setId(RESOURCE_ID);
        BaseEvent event = new BaseEvent(DATA_UPDATE, scenario, mock(ObjectMapper.class));


        //call the method
        streamApi.listenDatabaseUpdate(event);

        //capture the event and verify data
        ArgumentCaptor<ServerSentEvent> captor = ArgumentCaptor.forClass(ServerSentEvent.class);
        verify(mockSink).next(captor.capture());

        ServerSentEvent<?> serverSentEvent = captor.getValue();
        BaseEvent baseEventCaptured = (BaseEvent) serverSentEvent.data();
        assertEquals(DATA_DELETE, baseEventCaptured.getType());
        assertTrue(baseEventCaptured.getInstance() instanceof Scenario);
        assertEquals(scenario.getId(), ((Scenario) baseEventCaptured.getInstance()).getId());
    }


}
