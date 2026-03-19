package io.openaev.opencti.service;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Group;
import io.openaev.database.model.Role;
import io.openaev.database.model.User;
import io.openaev.opencti.client.OpenCTIClient;
import io.openaev.opencti.client.mutations.Ping;
import io.openaev.opencti.client.mutations.PushStixBundle;
import io.openaev.opencti.client.mutations.QueryTypeFields;
import io.openaev.opencti.client.mutations.RegisterConnector;
import io.openaev.opencti.client.response.Response;
import io.openaev.opencti.connectors.ConnectorBase;
import io.openaev.opencti.connectors.Constants;
import io.openaev.opencti.errors.ConnectorError;
import io.openaev.service.GroupService;
import io.openaev.service.RoleService;
import io.openaev.service.UserService;
import io.openaev.stix.objects.Bundle;
import io.openaev.stix.types.Identifier;
import io.openaev.utils.fixtures.GroupFixture;
import io.openaev.utils.fixtures.RoleFixture;
import io.openaev.utils.fixtures.TokenFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.composers.GroupComposer;
import io.openaev.utils.fixtures.composers.RoleComposer;
import io.openaev.utils.fixtures.composers.TokenComposer;
import io.openaev.utils.fixtures.composers.UserComposer;
import io.openaev.utils.fixtures.opencti.ConnectorFixture;
import io.openaev.utils.fixtures.opencti.ResponseFixture;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@DisplayName("OpenCTI Service tests")
public class OpenCTIServiceTest extends IntegrationTest {
  @MockBean private OpenCTIClient mockOpenCTIClient;
  @Autowired private OpenCTIService openCTIService;
  @Autowired private ObjectMapper mapper;
  @Autowired private RoleService roleService;
  @Autowired private GroupService groupService;
  @Autowired private UserService userService;
  @Autowired private EntityManager entityManager;
  @Autowired private RoleComposer roleComposer;
  @Autowired private GroupComposer groupComposer;
  @Autowired private UserComposer userComposer;
  @Autowired private TokenComposer tokenComposer;

  @BeforeEach
  public void setup() {
    roleComposer.reset();
    groupComposer.reset();
    userComposer.reset();
    tokenComposer.reset();
  }

  @Nested
  @DisplayName("Response parsing tests")
  public class ResponseParsingTests {
    @Nested
    @DisplayName("For registering connectors")
    public class ForRegisteringConnectors {
      @Test
      @DisplayName("When request crashes, throw exception")
      public void whenRequestCrashes_throwException() throws IOException {
        ConnectorBase testConnector = ConnectorFixture.getDefaultConnector();
        when(mockOpenCTIClient.execute(any(), any(), any(RegisterConnector.class)))
            .thenThrow(IOException.class);
        Response jwksSchemaResponse = ResponseFixture.getSchemaResponseWithJwks();
        when(mockOpenCTIClient.execute(any(), any(), any(QueryTypeFields.class)))
            .thenReturn(jwksSchemaResponse);

        assertThatThrownBy(() -> openCTIService.registerConnector(testConnector))
            .isInstanceOf(IOException.class);
      }

      @Test
      @DisplayName("When response has errors in it, throw exception")
      public void whenResponseHasErrorsInIt_throwException() throws IOException {
        ConnectorBase testConnector = ConnectorFixture.getDefaultConnector();
        Response errorResponse = ResponseFixture.getErrorResponse();
        when(mockOpenCTIClient.execute(any(), any(), any(RegisterConnector.class)))
            .thenReturn(errorResponse);
        Response jwksSchemaResponse = ResponseFixture.getSchemaResponseWithJwks();
        when(mockOpenCTIClient.execute(any(), any(), any(QueryTypeFields.class)))
            .thenReturn(jwksSchemaResponse);

        assertThatThrownBy(() -> openCTIService.registerConnector(testConnector))
            .isInstanceOf(ConnectorError.class)
            .hasMessageContaining(errorResponse.getErrors().getFirst().getMessage())
            .hasMessageContaining(
                "Failed to register connector %s with OpenCTI at %s"
                    .formatted(testConnector.getName(), testConnector.getUrl()));
      }

      @Test
      @DisplayName("When response is valid, return correct payload")
      public void whenResponseIsValid_returnCorrectPayload() throws IOException, ConnectorError {
        ConnectorBase testConnector = ConnectorFixture.getDefaultConnector();
        Response okResponse = ResponseFixture.getOkResponse();
        String payloadText =
            """
          {
            "registerConnector": {
              "id": "%s",
              "connector_state": null,
              "jwks": "{}",
              "config": {
                "connection": {
                  "host": "some host",
                  "vhost": "some vhost",
                  "use_ssl": true,
                  "port": 1234,
                  "user": "some user",
                  "pass": "some pass"
                },
                "listen": "some listen",
                "listen_routing": "some listen routing",
                "listen_exchange": "some listen exchange",
                "push": "some push",
                "push_routing": "some push routing",
                "push_exchange": "some push exchange"
              },
              "connector_user_id": "some user id"
            }
          }
          """
                .formatted(testConnector.getId());
        okResponse.setData((ObjectNode) mapper.readTree(payloadText));

        when(mockOpenCTIClient.execute(any(), any(), any(RegisterConnector.class)))
            .thenReturn(okResponse);
        Response jwksSchemaResponse = ResponseFixture.getSchemaResponseWithJwks();
        when(mockOpenCTIClient.execute(any(), any(), any(QueryTypeFields.class)))
            .thenReturn(jwksSchemaResponse);

        RegisterConnector.ResponsePayload payload = openCTIService.registerConnector(testConnector);

        assertThatJson(mapper.valueToTree(payload)).isEqualTo(payloadText);
        assertThat(testConnector.isRegistered()).isTrue();
      }
    }

    @Nested
    @DisplayName("For pinging connectors")
    public class ForPingingConnectors {
      @Test
      @DisplayName("When response has errors in it, throw exception")
      public void whenResponseHasErrorsInIt_throwException() throws IOException {
        ConnectorBase testConnector = ConnectorFixture.getDefaultConnector();
        testConnector.setRegistered(true); // so it can ping!
        Response errorResponse = ResponseFixture.getErrorResponse();
        when(mockOpenCTIClient.execute(any(), any(), any(Ping.class))).thenReturn(errorResponse);
        Response jwksSchemaResponse = ResponseFixture.getSchemaResponseWithJwks();
        when(mockOpenCTIClient.execute(any(), any(), any(QueryTypeFields.class)))
            .thenReturn(jwksSchemaResponse);

        assertThatThrownBy(() -> openCTIService.pingConnector(testConnector))
            .isInstanceOf(ConnectorError.class)
            .hasMessageContaining(errorResponse.getErrors().getFirst().getMessage())
            .hasMessageContaining(
                "Failed to ping connector %s with OpenCTI at %s"
                    .formatted(testConnector.getName(), testConnector.getUrl()));
      }

      @Test
      @DisplayName("When request crashes, throw exception")
      public void whenRequestCrashes_throwException() throws IOException {
        ConnectorBase testConnector = ConnectorFixture.getDefaultConnector();
        testConnector.setRegistered(true); // so it can ping!
        Response jwksSchemaResponse = ResponseFixture.getSchemaResponseWithJwks();
        when(mockOpenCTIClient.execute(any(), any(), any(QueryTypeFields.class)))
            .thenReturn(jwksSchemaResponse);
        when(mockOpenCTIClient.execute(any(), any(), any(Ping.class))).thenThrow(IOException.class);

        assertThatThrownBy(() -> openCTIService.pingConnector(testConnector))
            .isInstanceOf(IOException.class);
      }

      @Test
      @DisplayName("When response is valid, return correct payload")
      public void whenResponseIsValid_returnCorrectPayload() throws IOException, ConnectorError {
        ConnectorBase testConnector = ConnectorFixture.getDefaultConnector();
        testConnector.setRegistered(true); // so it can ping !
        Response okResponse = ResponseFixture.getOkResponse();
        String payloadText =
            """
          {
            "pingConnector": {
              "id": "%s",
              "jwks": "{}",
              "connector_state": null,
              "connector_info": {
                "run_and_terminate": false,
                "buffering": false,
                "queue_threshold": 0.0,
                "queue_messages_size": 0.0,
                "next_run_datetime": null,
                "last_run_datetime": null
              }
            }
          }
          """
                .formatted(testConnector.getId());
        okResponse.setData((ObjectNode) mapper.readTree(payloadText));

        when(mockOpenCTIClient.execute(any(), any(), any(Ping.class))).thenReturn(okResponse);
        Response jwksSchemaResponse = ResponseFixture.getSchemaResponseWithJwks();
        when(mockOpenCTIClient.execute(any(), any(), any(QueryTypeFields.class)))
            .thenReturn(jwksSchemaResponse);

        Ping.ResponsePayload payload = openCTIService.pingConnector(testConnector);

        assertThatJson(mapper.valueToTree(payload)).isEqualTo(payloadText);
      }

      @Test
      @DisplayName("When connector not yet registered, throw exception")
      public void wheConnectorNotYetRegistered_throwException() throws IOException {
        ConnectorBase testConnector = ConnectorFixture.getDefaultConnector();
        when(mockOpenCTIClient.execute(any(), any(), any(Ping.class)))
            .thenReturn(ResponseFixture.getOkResponse());

        assertThatThrownBy(() -> openCTIService.pingConnector(testConnector))
            .isInstanceOf(ConnectorError.class)
            .hasMessage(
                "Cannot ping connector %s with OpenCTI at %s: connector hasn't registered yet. Try again later."
                    .formatted(testConnector.getName(), testConnector.getUrl()));
      }
    }

    @Nested
    @DisplayName("For pushing STIX bundle")
    public class ForPushingSTIXBundle {
      private Bundle createBundle() {
        return new Bundle(new Identifier("titi"), List.of());
      }

      @Test
      @DisplayName("When request crashes, throw exception")
      public void whenRequestCrashes_throwException() throws IOException {
        ConnectorBase testConnector = ConnectorFixture.getDefaultConnector();
        testConnector.setRegistered(true); // so it can push!
        when(mockOpenCTIClient.execute(any(), any(), any(PushStixBundle.class)))
            .thenThrow(IOException.class);

        assertThatThrownBy(() -> openCTIService.pushStixBundle(createBundle(), testConnector))
            .isInstanceOf(IOException.class);
      }

      @Test
      @DisplayName("When response has errors in it, throw exception")
      public void whenResponseHasErrorsInIt_throwException() throws IOException {
        ConnectorBase testConnector = ConnectorFixture.getDefaultConnector();
        testConnector.setRegistered(true); // so it can push!
        Response errorResponse = ResponseFixture.getErrorResponse();
        when(mockOpenCTIClient.execute(any(), any(), any(PushStixBundle.class)))
            .thenReturn(errorResponse);

        assertThatThrownBy(() -> openCTIService.pushStixBundle(createBundle(), testConnector))
            .isInstanceOf(ConnectorError.class)
            .hasMessageContaining(errorResponse.getErrors().getFirst().getMessage())
            .hasMessageContaining(
                "Failed to push STIX bundle via connector %s to OpenCTI at %s"
                    .formatted(testConnector.getName(), testConnector.getUrl()));
      }

      @Test
      @DisplayName("When response is valid, return correct payload")
      public void whenResponseIsValid_returnCorrectPayload() throws IOException, ConnectorError {
        ConnectorBase testConnector = ConnectorFixture.getDefaultConnector();
        testConnector.setRegistered(true); // so it can push!
        Response okResponse = ResponseFixture.getOkResponse();
        String payloadText =
            """
              {
                "stixBundlePush": true
              }
              """;
        okResponse.setData((ObjectNode) mapper.readTree(payloadText));

        when(mockOpenCTIClient.execute(any(), any(), any(PushStixBundle.class)))
            .thenReturn(okResponse);

        PushStixBundle.ResponsePayload payload =
            openCTIService.pushStixBundle(createBundle(), testConnector);

        assertThatJson(mapper.valueToTree(payload)).isEqualTo(payloadText);
        assertThat(testConnector.isRegistered()).isTrue();
      }

      @Test
      @DisplayName("When connector not yet registered, throw exception")
      public void wheConnectorNotYetRegistered_throwException() throws IOException {
        ConnectorBase testConnector = ConnectorFixture.getDefaultConnector();
        when(mockOpenCTIClient.execute(any(), any(), any(PushStixBundle.class)))
            .thenReturn(ResponseFixture.getOkResponse());

        assertThatThrownBy(() -> openCTIService.pushStixBundle(createBundle(), testConnector))
            .isInstanceOf(ConnectorError.class)
            .hasMessage(
                "Cannot push STIX bundle via connector %s to OpenCTI at %s: connector hasn't registered yet. Try again later."
                    .formatted(testConnector.getName(), testConnector.getUrl()));
      }
    }
  }

  @Nested
  @DisplayName("Automated privileges provisioning")
  public class AutomatedPrivilegesProvisioning {
    @Nested
    @DisplayName("On register()")
    public class OnRegister {

      @Test
      @DisplayName("When the specific role does not exist, it is created on register")
      public void whenTheSpecificRoleDoesNotExist_itIsCreatedOnRegister()
          throws IOException, ConnectorError {
        ConnectorBase testConnector = ConnectorFixture.getDefaultConnector();
        Response okResponse = ResponseFixture.getOkResponse();
        when(mockOpenCTIClient.execute(any(), any(), any(RegisterConnector.class)))
            .thenReturn(okResponse);
        Response jwksSchemaResponse = ResponseFixture.getSchemaResponseWithJwks();
        when(mockOpenCTIClient.execute(any(), any(), any(QueryTypeFields.class)))
            .thenReturn(jwksSchemaResponse);

        openCTIService.registerConnector(testConnector);
        entityManager.flush();
        entityManager.clear();

        Optional<Role> role = roleService.findById(Constants.PROCESS_STIX_ROLE_ID);

        assertThat(role).isNotEmpty();
        assertThat(role.orElseThrow().getCapabilities())
            .isEqualTo(Constants.PROCESS_STIX_ROLE_CAPABILITIES);
        assertThat(role.get().getName()).isEqualTo(Constants.PROCESS_STIX_ROLE_NAME);
        assertThat(role.get().getDescription()).isEqualTo(Constants.PROCESS_STIX_ROLE_DESCRIPTION);
      }

      @Test
      @DisplayName(
          "When the specific role already exists, it is updated on register with correct attributes")
      public void whenTheSpecificRoleAlreadyExists_itIsUpdatedOnRegisterWithCorrectAttributes()
          throws IOException, ConnectorError {
        Role specificRole = RoleFixture.getRole();
        specificRole.setId(Constants.PROCESS_STIX_ROLE_ID);
        specificRole.setName("bad name");
        specificRole.setDescription("bad description");
        specificRole.setCapabilities(Set.of());
        roleComposer.forRole(specificRole).persist();
        entityManager.flush();
        entityManager.clear();

        ConnectorBase testConnector = ConnectorFixture.getDefaultConnector();
        Response okResponse = ResponseFixture.getOkResponse();
        when(mockOpenCTIClient.execute(any(), any(), any(RegisterConnector.class)))
            .thenReturn(okResponse);
        Response jwksSchemaResponse = ResponseFixture.getSchemaResponseWithJwks();
        when(mockOpenCTIClient.execute(any(), any(), any(QueryTypeFields.class)))
            .thenReturn(jwksSchemaResponse);

        openCTIService.registerConnector(testConnector);
        entityManager.flush();
        entityManager.clear();

        Optional<Role> role = roleService.findById(Constants.PROCESS_STIX_ROLE_ID);

        assertThat(role).isNotEmpty();
        assertThat(role.get().getCapabilities())
            .isEqualTo(Constants.PROCESS_STIX_ROLE_CAPABILITIES);
        assertThat(role.get().getName()).isEqualTo(Constants.PROCESS_STIX_ROLE_NAME);
        assertThat(role.get().getDescription()).isEqualTo(Constants.PROCESS_STIX_ROLE_DESCRIPTION);
      }

      @Test
      @DisplayName("When the specific group does not exist, it is created on register")
      public void whenTheSpecificGroupDoesNotExist_itIsCreatedOnRegister()
          throws IOException, ConnectorError {
        ConnectorBase testConnector = ConnectorFixture.getDefaultConnector();
        Response okResponse = ResponseFixture.getOkResponse();
        when(mockOpenCTIClient.execute(any(), any(), any(RegisterConnector.class)))
            .thenReturn(okResponse);
        Response jwksSchemaResponse = ResponseFixture.getSchemaResponseWithJwks();
        when(mockOpenCTIClient.execute(any(), any(), any(QueryTypeFields.class)))
            .thenReturn(jwksSchemaResponse);

        openCTIService.registerConnector(testConnector);
        entityManager.flush();
        entityManager.clear();

        Optional<Group> group = groupService.findById(Constants.PROCESS_STIX_GROUP_ID);

        assertThat(group).isNotEmpty();
        assertThat(group.get().getName()).isEqualTo(Constants.PROCESS_STIX_GROUP_NAME);
        assertThat(group.get().getDescription())
            .isEqualTo(Constants.PROCESS_STIX_GROUP_DESCRIPTION);
        assertThat(group.get().getRoles().stream().map(Role::getId).toList())
            .isEqualTo(List.of(Constants.PROCESS_STIX_ROLE_ID));
      }

      @Test
      @DisplayName(
          "When the specific group already exists, it is updated on register with correct attributes")
      public void whenTheSpecificGroupAlreadyExists_itIsUpdatedOnRegisterWithCorrectAttributes()
          throws IOException, ConnectorError {
        Group specificGroup = GroupFixture.createGroup();
        specificGroup.setId(Constants.PROCESS_STIX_GROUP_ID);
        specificGroup.setName("bad name");
        specificGroup.setDescription("bad description");
        specificGroup.setRoles(new ArrayList<>());
        groupComposer.forGroup(specificGroup).persist();
        entityManager.flush();
        entityManager.clear();

        ConnectorBase testConnector = ConnectorFixture.getDefaultConnector();
        Response okResponse = ResponseFixture.getOkResponse();
        when(mockOpenCTIClient.execute(any(), any(), any(RegisterConnector.class)))
            .thenReturn(okResponse);
        Response jwksSchemaResponse = ResponseFixture.getSchemaResponseWithJwks();
        when(mockOpenCTIClient.execute(any(), any(), any(QueryTypeFields.class)))
            .thenReturn(jwksSchemaResponse);

        openCTIService.registerConnector(testConnector);
        entityManager.flush();
        entityManager.clear();

        Optional<Group> group = groupService.findById(Constants.PROCESS_STIX_GROUP_ID);

        assertThat(group).isNotEmpty();
        assertThat(group.get().getName()).isEqualTo(Constants.PROCESS_STIX_GROUP_NAME);
        assertThat(group.get().getDescription())
            .isEqualTo(Constants.PROCESS_STIX_GROUP_DESCRIPTION);
        assertThat(group.get().getRoles().stream().map(Role::getId).toList())
            .isEqualTo(List.of(Constants.PROCESS_STIX_ROLE_ID));
      }

      @Test
      @DisplayName("When the specific user does not exist, it is created on register")
      public void whenTheSpecificUserDoesNotExist_itIsCreatedOnRegister()
          throws IOException, ConnectorError {
        ConnectorBase testConnector = ConnectorFixture.getDefaultConnector();
        Response okResponse = ResponseFixture.getOkResponse();
        when(mockOpenCTIClient.execute(any(), any(), any(RegisterConnector.class)))
            .thenReturn(okResponse);
        Response jwksSchemaResponse = ResponseFixture.getSchemaResponseWithJwks();
        when(mockOpenCTIClient.execute(any(), any(), any(QueryTypeFields.class)))
            .thenReturn(jwksSchemaResponse);

        openCTIService.registerConnector(testConnector);
        entityManager.flush();
        entityManager.clear();

        Optional<User> user = userService.findByToken(testConnector.getToken());

        assertThat(user).isNotEmpty();
        assertThat(user.get().getEmail())
            .isEqualTo("connector-%s@openaev.invalid".formatted(testConnector.getId()));
        assertThat(user.get().getFirstname()).isEqualTo(testConnector.getName());
        assertThat(user.get().getGroups().stream().map(Group::getId).toList())
            .isEqualTo(List.of(Constants.PROCESS_STIX_GROUP_ID));
      }

      @Test
      @DisplayName(
          "When the specific user already exists, it is updated on register with correct attributes")
      public void whenTheSpecificUserAlreadyExists_itIsUpdatedOnRegisterWithCorrectAttributes()
          throws IOException, ConnectorError {
        ConnectorBase testConnector = ConnectorFixture.getDefaultConnector();

        User specificUser = UserFixture.getUser();
        specificUser.setFirstname("bad firstname");
        specificUser.setEmail("bad_email@domain.invalid");
        specificUser.setGroups(List.of());
        userComposer
            .forUser(specificUser)
            .withToken(
                tokenComposer.forToken(TokenFixture.getTokenWithValue(testConnector.getToken())))
            .persist();
        entityManager.flush();
        entityManager.clear();

        Response okResponse = ResponseFixture.getOkResponse();
        when(mockOpenCTIClient.execute(any(), any(), any(RegisterConnector.class)))
            .thenReturn(okResponse);
        Response jwksSchemaResponse = ResponseFixture.getSchemaResponseWithJwks();
        when(mockOpenCTIClient.execute(any(), any(), any(QueryTypeFields.class)))
            .thenReturn(jwksSchemaResponse);

        openCTIService.registerConnector(testConnector);
        entityManager.flush();
        entityManager.clear();

        Optional<User> user = userService.findByToken(testConnector.getToken());

        assertThat(user).isNotEmpty();
        assertThat(user.get().getEmail())
            .isEqualTo("connector-%s@openaev.invalid".formatted(testConnector.getId()));
        assertThat(user.get().getFirstname()).isEqualTo(testConnector.getName());
        assertThat(user.get().getGroups().stream().map(Group::getId).toList())
            .isEqualTo(List.of(Constants.PROCESS_STIX_GROUP_ID));
      }
    }

    @Nested
    @DisplayName("On ping()")
    public class OnPing {

      @Test
      @DisplayName("When the specific role does not exist, it is created on ping")
      public void whenTheSpecificRoleDoesNotExist_itIsCreatedOnPing()
          throws IOException, ConnectorError {
        ConnectorBase testConnector = ConnectorFixture.getDefaultConnector();
        testConnector.setRegistered(true);
        Response okResponse = ResponseFixture.getOkResponse();
        when(mockOpenCTIClient.execute(any(), any(), any(Ping.class))).thenReturn(okResponse);
        Response jwksSchemaResponse = ResponseFixture.getSchemaResponseWithJwks();
        when(mockOpenCTIClient.execute(any(), any(), any(QueryTypeFields.class)))
            .thenReturn(jwksSchemaResponse);

        openCTIService.pingConnector(testConnector);
        entityManager.flush();
        entityManager.clear();

        Optional<Role> role = roleService.findById(Constants.PROCESS_STIX_ROLE_ID);

        assertThat(role).isNotEmpty();
        assertThat(role.get().getCapabilities())
            .isEqualTo(Constants.PROCESS_STIX_ROLE_CAPABILITIES);
        assertThat(role.get().getName()).isEqualTo(Constants.PROCESS_STIX_ROLE_NAME);
        assertThat(role.get().getDescription()).isEqualTo(Constants.PROCESS_STIX_ROLE_DESCRIPTION);
      }

      @Test
      @DisplayName(
          "When the specific role already exists, it is updated on ping with correct attributes")
      public void whenTheSpecificRoleAlreadyExists_itIsUpdatedOnPingWithCorrectAttributes()
          throws IOException, ConnectorError {
        Role specificRole = RoleFixture.getRole();
        specificRole.setId(Constants.PROCESS_STIX_ROLE_ID);
        specificRole.setName("bad name");
        specificRole.setDescription("bad description");
        specificRole.setCapabilities(Set.of());
        roleComposer.forRole(specificRole).persist();
        entityManager.flush();
        entityManager.clear();

        ConnectorBase testConnector = ConnectorFixture.getDefaultConnector();
        testConnector.setRegistered(true);
        Response okResponse = ResponseFixture.getOkResponse();
        when(mockOpenCTIClient.execute(any(), any(), any(Ping.class))).thenReturn(okResponse);
        Response jwksSchemaResponse = ResponseFixture.getSchemaResponseWithJwks();
        when(mockOpenCTIClient.execute(any(), any(), any(QueryTypeFields.class)))
            .thenReturn(jwksSchemaResponse);

        openCTIService.pingConnector(testConnector);
        entityManager.flush();
        entityManager.clear();

        Optional<Role> role = roleService.findById(Constants.PROCESS_STIX_ROLE_ID);

        assertThat(role).isNotEmpty();
        assertThat(role.get().getCapabilities())
            .isEqualTo(Constants.PROCESS_STIX_ROLE_CAPABILITIES);
        assertThat(role.get().getName()).isEqualTo(Constants.PROCESS_STIX_ROLE_NAME);
        assertThat(role.get().getDescription()).isEqualTo(Constants.PROCESS_STIX_ROLE_DESCRIPTION);
      }

      @Test
      @DisplayName("When the specific group does not exist, it is created on ping")
      public void whenTheSpecificGroupDoesNotExist_itIsCreatedOnPing()
          throws IOException, ConnectorError {
        ConnectorBase testConnector = ConnectorFixture.getDefaultConnector();
        testConnector.setRegistered(true);
        Response okResponse = ResponseFixture.getOkResponse();
        when(mockOpenCTIClient.execute(any(), any(), any(Ping.class))).thenReturn(okResponse);
        Response jwksSchemaResponse = ResponseFixture.getSchemaResponseWithJwks();
        when(mockOpenCTIClient.execute(any(), any(), any(QueryTypeFields.class)))
            .thenReturn(jwksSchemaResponse);

        openCTIService.pingConnector(testConnector);
        entityManager.flush();
        entityManager.clear();

        Optional<Group> group = groupService.findById(Constants.PROCESS_STIX_GROUP_ID);

        assertThat(group).isNotEmpty();
        assertThat(group.get().getName()).isEqualTo(Constants.PROCESS_STIX_GROUP_NAME);
        assertThat(group.get().getDescription())
            .isEqualTo(Constants.PROCESS_STIX_GROUP_DESCRIPTION);
        assertThat(group.get().getRoles().stream().map(Role::getId).toList())
            .isEqualTo(List.of(Constants.PROCESS_STIX_ROLE_ID));
      }

      @Test
      @DisplayName(
          "When the specific group already exists, it is updated on ping with correct attributes")
      public void whenTheSpecificGroupAlreadyExists_itIsUpdatedOnPingWithCorrectAttributes()
          throws IOException, ConnectorError {
        Group specificGroup = GroupFixture.createGroup();
        specificGroup.setId(Constants.PROCESS_STIX_GROUP_ID);
        specificGroup.setName("bad name");
        specificGroup.setDescription("bad description");
        specificGroup.setRoles(new ArrayList<>());
        groupComposer.forGroup(specificGroup).persist();
        entityManager.flush();
        entityManager.clear();

        ConnectorBase testConnector = ConnectorFixture.getDefaultConnector();
        testConnector.setRegistered(true);
        Response okResponse = ResponseFixture.getOkResponse();
        when(mockOpenCTIClient.execute(any(), any(), any(Ping.class))).thenReturn(okResponse);
        Response jwksSchemaResponse = ResponseFixture.getSchemaResponseWithJwks();
        when(mockOpenCTIClient.execute(any(), any(), any(QueryTypeFields.class)))
            .thenReturn(jwksSchemaResponse);

        openCTIService.pingConnector(testConnector);
        entityManager.flush();
        entityManager.clear();

        Optional<Group> group = groupService.findById(Constants.PROCESS_STIX_GROUP_ID);

        assertThat(group).isNotEmpty();
        assertThat(group.get().getName()).isEqualTo(Constants.PROCESS_STIX_GROUP_NAME);
        assertThat(group.get().getDescription())
            .isEqualTo(Constants.PROCESS_STIX_GROUP_DESCRIPTION);
        assertThat(group.get().getRoles().stream().map(Role::getId).toList())
            .isEqualTo(List.of(Constants.PROCESS_STIX_ROLE_ID));
      }

      @Test
      @DisplayName("When the specific user does not exist, it is created on ping")
      public void whenTheSpecificUserDoesNotExist_itIsCreatedOnPing()
          throws IOException, ConnectorError {
        ConnectorBase testConnector = ConnectorFixture.getDefaultConnector();
        testConnector.setRegistered(true);
        Response okResponse = ResponseFixture.getOkResponse();
        when(mockOpenCTIClient.execute(any(), any(), any(Ping.class))).thenReturn(okResponse);
        Response jwksSchemaResponse = ResponseFixture.getSchemaResponseWithJwks();
        when(mockOpenCTIClient.execute(any(), any(), any(QueryTypeFields.class)))
            .thenReturn(jwksSchemaResponse);

        openCTIService.pingConnector(testConnector);
        entityManager.flush();
        entityManager.clear();

        Optional<User> user = userService.findByToken(testConnector.getToken());

        assertThat(user).isNotEmpty();
        assertThat(user.get().getEmail())
            .isEqualTo("connector-%s@openaev.invalid".formatted(testConnector.getId()));
        assertThat(user.get().getFirstname()).isEqualTo(testConnector.getName());
        assertThat(user.get().getGroups().stream().map(Group::getId).toList())
            .isEqualTo(List.of(Constants.PROCESS_STIX_GROUP_ID));
      }

      @Test
      @DisplayName(
          "When the specific user already exists, it is updated on ping with correct attributes")
      public void whenTheSpecificUserAlreadyExists_itIsUpdatedOnPingWithCorrectAttributes()
          throws IOException, ConnectorError {
        ConnectorBase testConnector = ConnectorFixture.getDefaultConnector();
        testConnector.setRegistered(true);

        User specificUser = UserFixture.getUser();
        specificUser.setFirstname("bad firstname");
        specificUser.setEmail("bad_email@domain.invalid");
        specificUser.setGroups(List.of());
        userComposer
            .forUser(specificUser)
            .withToken(
                tokenComposer.forToken(TokenFixture.getTokenWithValue(testConnector.getToken())))
            .persist();
        entityManager.flush();
        entityManager.clear();

        Response okResponse = ResponseFixture.getOkResponse();
        when(mockOpenCTIClient.execute(any(), any(), any(Ping.class))).thenReturn(okResponse);
        Response jwksSchemaResponse = ResponseFixture.getSchemaResponseWithJwks();
        when(mockOpenCTIClient.execute(any(), any(), any(QueryTypeFields.class)))
            .thenReturn(jwksSchemaResponse);

        openCTIService.pingConnector(testConnector);
        entityManager.flush();
        entityManager.clear();

        Optional<User> user = userService.findByToken(testConnector.getToken());

        assertThat(user).isNotEmpty();
        assertThat(user.get().getEmail())
            .isEqualTo("connector-%s@openaev.invalid".formatted(testConnector.getId()));
        assertThat(user.get().getFirstname()).isEqualTo(testConnector.getName());
        assertThat(user.get().getGroups().stream().map(Group::getId).toList())
            .isEqualTo(List.of(Constants.PROCESS_STIX_GROUP_ID));
      }
    }
  }
}
