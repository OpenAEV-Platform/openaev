package io.openaev.rest;

import static io.openaev.rest.tag.TagApi.TAG_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Tag;
import io.openaev.rest.tag.form.TagCreateInput;
import io.openaev.rest.tag.form.TagUpdateInput;
import io.openaev.utils.fixtures.TagFixture;
import io.openaev.utils.fixtures.composers.TagComposer;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@DisplayName("Tag API capability checks")
public class TagApiCapabilityTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TagComposer tagComposer;

  @BeforeEach
  void beforeEach() {
    tagComposer.reset();
  }

  @Nested
  @DisplayName("Read/Search permissions")
  class ReadSearchPermissions {

    @Test
    @WithMockUser(withCapabilities = {Capability.ACCESS_TAGS})
    @DisplayName("Given ACCESS_TAGS, should list and search tags")
    void given_accessTags_should_listAndSearchTags() throws Exception {
      // -------- Arrange --------
      tagComposer.forTag(TagFixture.getTagWithText("ReadTag")).persist();
      SearchPaginationInput input = new SearchPaginationInput();
      input.setTextSearch("readtag");
      input.setSize(10);
      input.setPage(0);

      // -------- Act --------
      String listResponse =
          mvc.perform(get(TAG_URI).accept(MediaType.APPLICATION_JSON).with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String searchResponse =
          mvc.perform(
                  post(TAG_URI + "/search")
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -------- Assert --------
      List<String> tagNames = JsonPath.read(listResponse, "$[*].tag_name");
      assertTrue(tagNames.contains("readtag"));
      assertEquals(Integer.valueOf(1), JsonPath.read(searchResponse, "$.totalElements"));
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TAGS})
    @DisplayName("Given MANAGE_TAGS, should read tags through manager capability inheritance")
    void given_manageTags_should_readTagsThroughManagerCapabilityInheritance() throws Exception {
      // -------- Arrange --------
      tagComposer.forTag(TagFixture.getTagWithText("ManagerReadTag")).persist();
      SearchPaginationInput input = new SearchPaginationInput();
      input.setTextSearch("managerreadtag");
      input.setSize(10);
      input.setPage(0);

      // -------- Act --------
      String searchResponse =
          mvc.perform(
                  post(TAG_URI + "/search")
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -------- Assert --------
      assertEquals(Integer.valueOf(1), JsonPath.read(searchResponse, "$.totalElements"));
    }

    @Test
    @WithMockUser
    @DisplayName("Given no tags capability, should allow list and search")
    void given_noTagsCapability_should_allowListAndSearch() throws Exception {
      // -------- Arrange --------
      SearchPaginationInput input = new SearchPaginationInput();
      input.setSize(10);
      input.setPage(0);

      // -------- Act & Assert --------
      mvc.perform(get(TAG_URI).accept(MediaType.APPLICATION_JSON).with(csrf()))
          .andExpect(status().is2xxSuccessful());

      mvc.perform(
              post(TAG_URI + "/search")
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());
    }
  }

  @Nested
  @DisplayName("Create permissions")
  class CreatePermissions {

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TAGS})
    @DisplayName("Given MANAGE_TAGS, should create a tag")
    void given_manageTags_should_createTag() throws Exception {
      // -------- Arrange --------
      TagCreateInput input = new TagCreateInput();
      input.setName("CreatedTag");
      input.setColor("#11AA22");

      // -------- Act --------
      String response =
          mvc.perform(
                  post(TAG_URI)
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -------- Assert --------
      assertEquals("createdtag", JsonPath.read(response, "$.tag_name"));
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.ACCESS_TAGS})
    @DisplayName("Given ACCESS_TAGS only, should be forbidden to create a tag")
    void given_accessTagsOnly_should_forbidCreateTag() throws Exception {
      // -------- Arrange --------
      TagCreateInput input = new TagCreateInput();
      input.setName("ForbiddenCreateTag");
      input.setColor("#12AB34");

      // -------- Act & Assert --------
      mvc.perform(
              post(TAG_URI)
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isForbidden());
    }
  }

  @Nested
  @DisplayName("Update permissions")
  class UpdatePermissions {

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TAGS})
    @DisplayName("Given MANAGE_TAGS, should update a tag")
    void given_manageTags_should_updateTag() throws Exception {
      // -------- Arrange --------
      Tag tag = tagComposer.forTag(TagFixture.getTagWithText("BeforeUpdate")).persist().get();
      TagUpdateInput input = new TagUpdateInput();
      input.setName("AfterUpdate");
      input.setColor("#ABCDEF");

      // -------- Act --------
      String response =
          mvc.perform(
                  put(TAG_URI + "/" + tag.getId())
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -------- Assert --------
      assertEquals("afterupdate", JsonPath.read(response, "$.tag_name"));
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.ACCESS_TAGS})
    @DisplayName("Given ACCESS_TAGS only, should be forbidden to update a tag")
    void given_accessTagsOnly_should_forbidUpdateTag() throws Exception {
      // -------- Arrange --------
      Tag tag = tagComposer.forTag(TagFixture.getTagWithText("NoUpdate")).persist().get();
      TagUpdateInput input = new TagUpdateInput();
      input.setName("ShouldFail");
      input.setColor("#112233");

      // -------- Act & Assert --------
      mvc.perform(
              put(TAG_URI + "/" + tag.getId())
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isForbidden());
    }
  }

  @Nested
  @DisplayName("Delete permissions")
  class DeletePermissions {

    @Test
    @WithMockUser(withCapabilities = {Capability.DELETE_TAGS})
    @DisplayName("Given DELETE_TAGS, should delete a tag")
    void given_deleteTags_should_deleteTag() throws Exception {
      // -------- Arrange --------
      Tag tag = tagComposer.forTag(TagFixture.getTagWithText("DeleteMe")).persist().get();

      // -------- Act & Assert --------
      mvc.perform(delete(TAG_URI + "/" + tag.getId()).accept(MediaType.APPLICATION_JSON).with(csrf()))
          .andExpect(status().is2xxSuccessful());
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TAGS})
    @DisplayName("Given MANAGE_TAGS only, should be forbidden to delete a tag")
    void given_manageTagsOnly_should_forbidDeleteTag() throws Exception {
      // -------- Arrange --------
      Tag tag = tagComposer.forTag(TagFixture.getTagWithText("NoDelete")).persist().get();

      // -------- Act & Assert --------
      mvc.perform(delete(TAG_URI + "/" + tag.getId()).accept(MediaType.APPLICATION_JSON).with(csrf()))
          .andExpect(status().isForbidden());
    }
  }
}


