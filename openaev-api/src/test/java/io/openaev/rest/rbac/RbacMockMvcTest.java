package io.openaev.rest.rbac;

import static io.openaev.service.UserService.buildAuthenticationToken;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import io.openaev.IntegrationTest;
import io.openaev.aop.RBAC;
import io.openaev.database.model.*;
import io.openaev.service.PermissionService;
import io.openaev.utils.fixtures.GrantFixture;
import io.openaev.utils.fixtures.GroupFixture;
import io.openaev.utils.fixtures.RoleFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.composers.GrantComposer;
import io.openaev.utils.fixtures.composers.GroupComposer;
import io.openaev.utils.fixtures.composers.RoleComposer;
import io.openaev.utils.fixtures.composers.UserComposer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@TestInstance(PER_CLASS)
@Disabled
public class RbacMockMvcTest extends IntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private RbacEndpointScanner rbacEndpointScanner;

  @Autowired private GroupComposer groupComposer;

  @Autowired private RoleComposer roleComposer;

  @Autowired private GrantComposer grantComposer;

  @Autowired private UserComposer userComposer;

  private static List<EndpointInfo> endpoints;

  private static final Set<EndpointKey> EXCLUDED_ENDPOINTS = Set.of();

  private List<String> errors = new ArrayList<>();

  record EndpointKey(String method, String path) {}

  @BeforeAll
  void setUp() {
    endpoints = rbacEndpointScanner.findRbacEndpoints();
  }

  @AfterAll
  void afterAll() {
    if (!errors.isEmpty()) {
      StringBuilder sb =
          new StringBuilder("Errors occurred during RBAC tests for the following endpoints:\n");
      for (String error : errors) {
        sb.append(" - ").append(error).append("\n");
      }
      throw new RuntimeException(sb.toString());
    }
  }

  @AfterEach
  void afterEach() {
    userComposer.reset();
    groupComposer.reset();
    roleComposer.reset();
    grantComposer.reset();
  }

  static Stream<Arguments> endpointTestScenarios() {
    return endpoints.stream()
        .flatMap(
            endpoint ->
                validScenariosFor(endpoint).stream()
                    .map(scenario -> Arguments.of(endpoint, scenario)));
  }

  @ParameterizedTest(name = "[{index}] {0} - {1}")
  @MethodSource("endpointTestScenarios")
  void endpointTestScenarios(EndpointInfo endpoint, EndpointTestScenarios endpointTestScenario)
      throws Exception {
    // Arrange
    MockHttpServletRequestBuilder request = createRequestBuilder(endpoint);
    Authentication auth =
        createAuthenticationForScenario(endpoint.getRbac(), endpointTestScenario, endpoint);
    SecurityContextHolder.getContext().setAuthentication(auth);

    // Act
    try {
      var result = mockMvc.perform(request).andReturn().getResponse();

      // Assert
      if (endpointTestScenario.shouldBeAllowed(endpoint.getRbac())) {
        assertNotEquals(401, result.getStatus());
        assertNotEquals(403, result.getStatus());
      } else {
        assertEquals(401, result.getStatus());
      }
    } catch (Throwable t) {
      errors.add(endpoint.getMethod() + endpoint.getPath());
    }
  }

  private MockHttpServletRequestBuilder createRequestBuilder(EndpointInfo ep) {
    String resolvedPath = resolvePathVariables(ep.getPath());
    MockHttpServletRequestBuilder builder;

    switch (ep.getMethod()) {
      case GET -> builder = get(resolvedPath);
      case POST -> builder = post(resolvedPath);
      case PUT -> builder = put(resolvedPath);
      case DELETE -> builder = delete(resolvedPath);
      default -> throw new IllegalArgumentException("Unsupported method: " + ep.getMethod());
    }

    // Handle content type
    if (ep.getConsumes().contains(MediaType.MULTIPART_FORM_DATA_VALUE)) {
      // Simulate a multipart request
      builder =
          multipart(resolvedPath).file("file", "dummy content".getBytes(StandardCharsets.UTF_8));
    } else if (!ep.getConsumes().isEmpty()) {
      // Use first content type and add dummy JSON body
      builder
          .contentType(MediaType.valueOf(ep.getConsumes().get(0)))
          .content("{}"); // Dummy JSON body
    }

    return builder;
  }

  private String resolvePathVariables(String path) {
    // Replace all {varName} with dummy UUIDs
    return path.replaceAll("\\{[^/]+?\\}", UUID.randomUUID().toString());
  }

  // -- Auth creation --
  private Authentication createAuthenticationForScenario(
      RBAC rbac, EndpointTestScenarios scenario, EndpointInfo endpointInfo) {
    // For unprotected endpoints and open resources, always return admin (we don't really care about
    // the user permissions in this case)
    if (rbac.skipRBAC()
        || PermissionService.isOpenResource(rbac.resourceType(), rbac.actionPerformed())) {
      return buildAuthenticationForAdmin();
    }
    return switch (scenario) {
      case ADMIN -> buildAuthenticationForAdmin();
      case GROUP_WITH_BYPASS -> buildAuthForRoleWithCapability(Capability.BYPASS, false, rbac);
      case GROUP_NO_ROLE, GROUP_ROLE_NO_CAPABILITY ->
          buildAuthForRoleWithCapability(null, false, rbac);
      case RESOURCE_GRANT_ONLY -> buildAuthForGrantOnly(rbac);
      case RESOURCE_ROLE_MATCH, NO_RESOURCE_ROLE_MATCH -> {
        Capability capa;
        if (ResourceType.INJECT.equals(rbac.resourceType())) {
          // INJECT corresponds either to ATOMIC_TESTING, SIMULATION or SCENARIO capa
          if (endpointInfo.getPath().startsWith("/api/atomic-testings/")
              || endpointInfo.getPath().contains("/atomic-testing/")) {
            capa = Capability.of(ResourceType.ATOMIC_TESTING, rbac.actionPerformed()).get();
          } else if (endpointInfo.getPath().startsWith("/api/exercises/")) {
            capa = Capability.of(ResourceType.SIMULATION, rbac.actionPerformed()).get();
          } else if (endpointInfo.getPath().startsWith("/api/scenarios/")) {
            capa = Capability.of(ResourceType.SCENARIO, rbac.actionPerformed()).get();
          } else if (endpointInfo.getPath().startsWith("/api/findings/")) {
            capa = Capability.of(ResourceType.FINDING, rbac.actionPerformed()).get();
          } else {
            capa = Capability.of(rbac.resourceType(), rbac.actionPerformed()).get();
          }
        } else if (ResourceType.SIMULATION_OR_SCENARIO.equals(rbac.resourceType())) {
          capa = Capability.of(ResourceType.SIMULATION, rbac.actionPerformed()).get();
        } else {
          capa = Capability.of(rbac.resourceType(), rbac.actionPerformed()).get();
        }
        yield buildAuthForRoleWithCapability(capa, false, rbac);
      }
    };
  }

  private Authentication buildAuthenticationForAdmin() {
    User adminUser =
        UserFixture.getAdminUser("Admin", "User", UUID.randomUUID() + "@unittests.invalid");

    return buildAuthenticationToken(userComposer.forUser(adminUser).persist().get());
  }

  private Authentication buildAuthForRoleWithCapability(
      Capability capability, boolean addGrant, RBAC rbac) {
    Group group = GroupFixture.createGroup();

    Set<Capability> capabilities = capability == null ? Set.of() : Set.of(capability);

    GroupComposer.Composer groupComposed =
        groupComposer
            .forGroup(group)
            .withRole(roleComposer.forRole(RoleFixture.getRole(capabilities)));

    User user =
        userComposer
            .forUser(UserFixture.getUser("First", "Last", UUID.randomUUID() + "@unittests.invalid"))
            .withGroup(groupComposed)
            .persist()
            .get();

    // Optionally add a grant
    if (addGrant && rbac != null && rbac.resourceId() != null && !rbac.resourceId().isBlank()) {
      Grant.GRANT_RESOURCE_TYPE grantResourceType =
          Grant.GRANT_RESOURCE_TYPE.fromRbacResourceType(rbac.resourceType());
      Grant.GRANT_TYPE grantType = Grant.GRANT_TYPE.fromRbacAction(rbac.actionPerformed());
      Grant grant = GrantFixture.getGrant(rbac.resourceId(), grantResourceType, grantType, group);
    }

    return buildAuthenticationToken(user);
  }

  private Authentication buildAuthForGrantOnly(RBAC rbac) {
    Group group = GroupFixture.createGroup();

    GroupComposer.Composer groupComposed = groupComposer.forGroup(group); // no roles

    // Add a grant matching the resourceId in the annotation
    Grant.GRANT_RESOURCE_TYPE grantResourceType =
        Grant.GRANT_RESOURCE_TYPE.fromRbacResourceType(rbac.resourceType());
    Grant.GRANT_TYPE grantType = Grant.GRANT_TYPE.fromRbacAction(rbac.actionPerformed());
    Grant grant = GrantFixture.getGrant(rbac.resourceId(), grantResourceType, grantType, group);
    groupComposed.withGrant(grantComposer.forGrant(grant));

    User user =
        userComposer
            .forUser(UserFixture.getUser("Grant", "Only", UUID.randomUUID() + "@unittests.invalid"))
            .withGroup(groupComposed)
            .persist()
            .get();

    return buildAuthenticationToken(user);
  }

  private String extractResourceId(RBAC rbac) {
    String raw = rbac.resourceId();
    if (raw == null || raw.isBlank()) return "dummy-resource-id"; // fallback

    // Dummy SpEL resolution for tests
    if (raw.startsWith("#")) {
      String param = raw.substring(1);
      return switch (param) {
        case "roleId" ->
            UUID.randomUUID().toString(); // you can replace with actual role ID in context
        default -> UUID.randomUUID().toString();
      };
    }

    return raw;
  }

  private static List<EndpointTestScenarios> validScenariosFor(EndpointInfo endpoint) {
    RBAC rbac = endpoint.getRbac();

    boolean hasResourceId = rbac.resourceId() != null && !rbac.resourceId().isBlank();

    List<EndpointTestScenarios> scenarios =
        new ArrayList<>(
            List.of(
                EndpointTestScenarios.ADMIN,
                EndpointTestScenarios.GROUP_WITH_BYPASS,
                EndpointTestScenarios.GROUP_NO_ROLE,
                EndpointTestScenarios.GROUP_ROLE_NO_CAPABILITY));

    if (hasResourceId) {
      scenarios.addAll(
          List.of(
              EndpointTestScenarios.RESOURCE_GRANT_ONLY,
              EndpointTestScenarios.RESOURCE_ROLE_MATCH));
    } else {
      scenarios.addAll(List.of(EndpointTestScenarios.NO_RESOURCE_ROLE_MATCH));
    }

    return scenarios;
  }
}
