package io.openbas.rest.role;

import static io.openbas.utils.JsonUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openbas.IntegrationTest;
import io.openbas.database.model.Capability;
import io.openbas.database.model.Role;
import io.openbas.database.repository.RoleRepository;
import io.openbas.rest.role.form.RoleInput;
import io.openbas.utils.fixtures.RoleFixture;
import io.openbas.utils.mockUser.WithMockUser;
import io.openbas.utils.pagination.SearchPaginationInput;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@TestInstance(PER_CLASS)
public class RoleApiTest extends IntegrationTest {

  public static final String ROLE_URI = "/api/roles";

  @Autowired private MockMvc mvc;

  @Autowired private RoleRepository roleRepository;
  @Autowired private RoleRepository groupRepository;

  Set<String> roleToDelete = new HashSet<>();

  @AfterEach
  void afterEach() {

    roleToDelete.stream().forEach(roleId -> roleRepository.deleteById(roleId));
    roleToDelete = new HashSet<>();
  }

  @Test
  @WithMockUser(isAdmin = true)
  void test_createRole() throws Exception {
    String roleName = "roleName";
    Capability capa1 = Capability.ACCESS_ASSETS;
    Capability capa2 = Capability.ACCESS_CHALLENGES;
    Set<Capability> capabilities = Set.of(capa1, capa2);

    // Create
    RoleInput roleInput = RoleInput.builder().name(roleName).capabilities(capabilities).build();
    String response =
        mvc.perform(
                post(ROLE_URI)
                    .content(asJsonString(roleInput))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    // -- ASSERT --
    String roleId = JsonPath.read(response, "$.role_id");
    assertNotNull(response);
    assertEquals(roleName, JsonPath.read(response, "$.role_name"));
    List<String> capabilitiesJson = JsonPath.read(response, "$.role_capabilities");
    Set<Capability> responseCapabilities =
        capabilitiesJson.stream().map(Capability::valueOf).collect(Collectors.toSet());
    assertEquals(capabilities, responseCapabilities);

    roleToDelete.add(roleId);
  }

  @Test
  @WithMockUser(isAdmin = true)
  void test_findRole() throws Exception {

    Role expectedRole = roleRepository.save(RoleFixture.getRole());
    // Find call
    String response =
        mvc.perform(get(ROLE_URI + "/" + expectedRole.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    assertNotNull(response);
    assertEquals(expectedRole.getName(), JsonPath.read(response, "$.role_name"));
    assertEquals(expectedRole.getId(), JsonPath.read(response, "$.role_id"));
    List<String> capabilitiesJson = JsonPath.read(response, "$.role_capabilities");
    assertEquals(
        expectedRole.getCapabilities(),
        capabilitiesJson.stream().map(Capability::valueOf).collect(Collectors.toSet()));

    roleToDelete.add(expectedRole.getId());
  }

  @Test
  @WithMockUser(isAdmin = true)
  void test_updateRole() throws Exception {
    String updatedRoleName = "roleNameUpdated";
    Role savedRole = roleRepository.save(RoleFixture.getRole());

    RoleInput input =
        RoleInput.builder().name(updatedRoleName).capabilities(savedRole.getCapabilities()).build();
    String response =
        mvc.perform(
                put(ROLE_URI + "/" + savedRole.getId())
                    .content(asJsonString(input))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    // -- ASSERT --
    assertNotNull(response);
    assertEquals(updatedRoleName, JsonPath.read(response, "$.role_name"));
    assertEquals(savedRole.getId(), JsonPath.read(response, "$.role_id"));
    List<String> capabilitiesJson = JsonPath.read(response, "$.role_capabilities");
    assertEquals(
        savedRole.getCapabilities(),
        capabilitiesJson.stream().map(Capability::valueOf).collect(Collectors.toSet()));

    roleToDelete.add(savedRole.getId());
  }

  @Test
  @WithMockUser(isAdmin = true)
  void test_deleteRole() throws Exception {
    Role savedRole = roleRepository.save(RoleFixture.getRole());

    mvc.perform(delete(ROLE_URI + "/" + savedRole.getId()).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful())
        .andReturn()
        .getResponse()
        .getContentAsString();

    assertFalse(roleRepository.existsById(savedRole.getId()));
  }

  @Test
  @WithMockUser(isAdmin = true)
  void test_searchRole() throws Exception {
    Role role1 = roleRepository.save(RoleFixture.getRole());
    Role role2 = roleRepository.save(RoleFixture.getRole());
    Role role3 = roleRepository.save(RoleFixture.getRole());
    Role role4 = roleRepository.save(RoleFixture.getRole());
    SearchPaginationInput input = new SearchPaginationInput();
    input.setSize(2);
    input.setPage(0);

    String response =
        mvc.perform(
                post(ROLE_URI + "/search")
                    .content(asJsonString(input))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertEquals(Integer.valueOf(2), JsonPath.read(response, "$.numberOfElements"));

    roleToDelete.addAll(List.of(role1.getId(), role2.getId(), role3.getId(), role4.getId()));
  }

  @Test
  @WithMockUser(isAdmin = true)
  void test_getAllRoles() throws Exception {
    Role role1 = roleRepository.save(RoleFixture.getRole());
    Role role2 = roleRepository.save(RoleFixture.getRole());
    Role role3 = roleRepository.save(RoleFixture.getRole());
    Role role4 = roleRepository.save(RoleFixture.getRole());

    Set<String> expectedRoleIds =
        Set.of(role1.getId(), role2.getId(), role3.getId(), role4.getId());
    String response =
        mvc.perform(
                get(ROLE_URI)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertNotNull(response);

    List<Map<String, Object>> roleList = JsonPath.read(response, "$");
    Set<String> returnedRoleIds =
        roleList.stream()
            .map(item -> Objects.toString(item.get("role_id"), null))
            .collect(Collectors.toSet());
    assertTrue(returnedRoleIds.containsAll(expectedRoleIds));

    roleToDelete.addAll(expectedRoleIds);
  }

  @Test
  @WithMockUser(isAdmin = true)
  void test_updateRole_WITH_nonexistent_id() throws Exception {

    RoleInput input = RoleInput.builder().name("test").capabilities(new HashSet<>()).build();

    mvc.perform(
            put(ROLE_URI + "/randomid")
                .content(asJsonString(input))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound());
  }

  @Test
  @WithMockUser(isAdmin = true)
  void test_deleteRole_WITH_nonexistent_id() throws Exception {

    mvc.perform(delete(ROLE_URI + "/randomid").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound());
  }

  @Test
  @WithMockUser(isAdmin = true)
  void test_findRole_WITH_nonexistent_id() throws Exception {

    mvc.perform(get(ROLE_URI + "/randomid").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound());
  }
}
