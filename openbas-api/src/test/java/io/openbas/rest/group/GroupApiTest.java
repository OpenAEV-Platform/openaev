package io.openbas.rest.group;

import static io.openbas.utils.JsonUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openbas.IntegrationTest;
import io.openbas.database.model.Group;
import io.openbas.database.model.Role;
import io.openbas.database.repository.GroupRepository;
import io.openbas.database.repository.RoleRepository;
import io.openbas.rest.group.form.GroupUpdateRolesInput;
import io.openbas.utils.fixtures.GroupFixture;
import io.openbas.utils.fixtures.RoleFixture;
import io.openbas.utils.mockUser.WithMockAdminUser;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@TestInstance(PER_CLASS)
public class GroupApiTest extends IntegrationTest {

  public static final String GROUP_URI = "/api/groups";

  @Autowired private MockMvc mvc;

  @Autowired private GroupRepository groupRepository;

  @Autowired private RoleRepository roleRepository;

  @AfterEach
  void afterEach() {
    groupRepository.deleteAll();
    roleRepository.deleteAll();
  }

  @Test
  @WithMockAdminUser
  void test_updateGroupRoles() throws Exception {

    Group group = groupRepository.save(GroupFixture.createGroup());
    Role role = roleRepository.save(RoleFixture.getRole());

    GroupUpdateRolesInput input =
        GroupUpdateRolesInput.builder().roleIds(List.of(role.getId())).build();
    String response =
        mvc.perform(
                put(GROUP_URI + "/" + group.getId() + "/roles")
                    .content(asJsonString(input))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    List<Role> roles = JsonPath.read(response, "$.group_roles");
    assertEquals(1, roles.size());
    assertEquals(role.getId(), roles.getFirst());
  }

  @Test
  @WithMockAdminUser
  void test_updateGroupRoles_WITH_unexisting_role_id() throws Exception {
    Group group = groupRepository.save(GroupFixture.createGroup());
    GroupUpdateRolesInput input =
        GroupUpdateRolesInput.builder().roleIds(List.of("randomid")).build();

    mvc.perform(
            put(GROUP_URI + "/" + group.getId() + "/roles")
                .content(asJsonString(input))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound());
  }

  @Test
  @WithMockAdminUser
  void test_updateGroupRoles_WITH_unexisting_group_id() throws Exception {
    GroupUpdateRolesInput input =
        GroupUpdateRolesInput.builder().roleIds(List.of("randomid")).build();

    mvc.perform(
            put(GROUP_URI + "/randomid/roles")
                .content(asJsonString(input))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound());
  }
}
