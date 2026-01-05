package io.openaev.rest.group;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Group;
import io.openaev.database.model.Role;
import io.openaev.database.repository.GroupRepository;
import io.openaev.database.repository.RoleRepository;
import io.openaev.rest.group.form.GroupCreateInput;
import io.openaev.rest.group.form.GroupUpdateRolesInput;
import io.openaev.utils.fixtures.GroupFixture;
import io.openaev.utils.fixtures.RoleFixture;
import io.openaev.utils.fixtures.composers.GroupComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
public class GroupApiTest extends IntegrationTest {

  public static final String GROUP_URI = "/api/groups";

  @Autowired private MockMvc mvc;

  @Autowired private GroupRepository groupRepository;

  @Autowired private RoleRepository roleRepository;

  @Autowired private GroupComposer groupComposer;

  @BeforeEach
  public void setup() {
    groupComposer.reset();
  }

  @Nested
  @DisplayName("Normal CRUD")
  public class NormalCRUD {
    @Test
    @DisplayName("Can create a group with input")
    public void canCreateAGroupWithInput() throws Exception {
      GroupCreateInput input = new GroupCreateInput();
      input.setName("Created group name");
      input.setDescription("Created group description");
      input.setDefaultUserAssignation(false);

      String response =
          mvc.perform(
                  post(GROUP_URI)
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThatJson(response)
          .whenIgnoringPaths("listened", "group_id")
          .isEqualTo(
              """
        {
          "group_name":"Created group name",
          "group_description":"Created group description",
          "group_default_user_assign":false,
          "group_grants":[],
          "group_users":[],
          "group_roles":[]
        }
        """);
    }

    @Test
    @DisplayName("Can update a group with input")
    public void canUpdateAGroupWithInput() throws Exception {
      GroupComposer.Composer groupWrapper =
          groupComposer.forGroup(GroupFixture.createGroup()).persist();

      GroupCreateInput input = new GroupCreateInput();
      input.setName("New created group name");
      input.setDescription("New created group description");
      input.setDefaultUserAssignation(true);

      String response =
          mvc.perform(
                  put(GROUP_URI + "/" + groupWrapper.get().getId() + "/information")
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThatJson(response)
          .whenIgnoringPaths("listened", "group_id")
          .isEqualTo(
              """
              {
                "group_name":"New created group name",
                "group_description":"New created group description",
                "group_default_user_assign":true,
                "group_grants":[],
                "group_users":[],
                "group_roles":[]
              }
              """);
    }
  }

  @Nested
  @DisplayName("Update group with roles")
  public class UpdateGroupWithRoles {
    @Test
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
}
