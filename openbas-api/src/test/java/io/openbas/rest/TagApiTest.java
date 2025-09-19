package io.openbas.rest;

import static io.openbas.rest.tag.TagApi.TAG_URI;
import static io.openbas.utils.JsonUtils.asJsonString;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openbas.IntegrationTest;
import io.openbas.database.model.Tag;
import io.openbas.rest.tag.form.TagUpdateInput;
import io.openbas.utils.fixtures.TagFixture;
import io.openbas.utils.fixtures.composers.TagComposer;
import io.openbas.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@DisplayName("Tag API tests")
public class TagApiTest extends IntegrationTest {
  @Autowired private TagComposer tagComposer;
  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper mapper;
  @Autowired private EntityManager entityManager;

  @BeforeEach
  void beforeEach() {
    tagComposer.reset();
  }

  @Test
  @WithMockUser(isAdmin = true)
  @DisplayName("When tag already exists, update tag changes properties and returns modified tag")
  public void whenTagAlreadyExists_updateTagChangesPropertiesAndReturnsTag() throws Exception {
    Tag tag = tagComposer.forTag(TagFixture.getTagWithText("mock text")).persist().get();
    String expectedName = "this is a modified tag";
    String expectedColour = "#201bce";

    TagUpdateInput input = new TagUpdateInput();
    input.setName(expectedName);
    input.setColor(expectedColour);

    entityManager.flush();
    entityManager.clear();

    // --EXECUTE--
    String response =
        mvc.perform(
                put(TAG_URI + "/" + tag.getId())
                    .content(asJsonString(input))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    Tag expected = TagFixture.getTagWithTextAndColour(expectedName, expectedColour);
    expected.setId(tag.getId());

    assertThatJson(response).isEqualTo(mapper.writeValueAsString(expected));
  }
}
