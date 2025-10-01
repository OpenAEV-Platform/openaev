package io.openaev.rest.tag_rule;

import static io.openaev.utils.JsonUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.Tag;
import io.openaev.database.model.TagRule;
import io.openaev.database.repository.AssetGroupRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.database.repository.TagRuleRepository;
import io.openaev.rest.tag_rule.form.TagRuleInput;
import io.openaev.utils.fixtures.AssetGroupFixture;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@TestInstance(PER_CLASS)
public class TagRuleApiTest extends IntegrationTest {

  public static final String TAG_RULE_URI = "/api/tag-rules";

  @Autowired private MockMvc mvc;

  @Autowired private AssetGroupRepository assetGroupRepository;
  @Autowired private TagRepository tagRepository;
  @Autowired private TagRuleRepository tagRuleRepository;

  @AfterEach
  void afterEach() {
    tagRuleRepository.deleteAll();
    tagRepository.deleteAll();
    assetGroupRepository.deleteAll();
  }

  @Test
  @WithMockUser(isAdmin = true)
  void findTagRule() throws Exception {
    String assetGroupName = "assetGroupName";
    String tagId = "tagName";
    TagRule expected = createTagRule(tagId, List.of(assetGroupName));

    String response =
        mvc.perform(get(TAG_RULE_URI + "/" + expected.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    // -- ASSERT --
    assertNotNull(response);
    assertEquals(expected.getId(), JsonPath.read(response, "$.tag_rule_id"));
    assertEquals(expected.getTag().getName(), JsonPath.read(response, "$.tag_name"));
    Map<String, Object> tagRuleAssetGroups = JsonPath.read(response, "$.asset_groups");
    assertTrue(tagRuleAssetGroups.containsKey(expected.getAssetGroups().getFirst().getId()));
  }

  @Test
  @WithMockUser(isAdmin = true)
  void deleteTagRule() throws Exception {
    String assetGroupName = "assetGroupName";
    String tagId = "tagName";
    TagRule expected = createTagRule(tagId, List.of(assetGroupName));
    mvc.perform(delete(TAG_RULE_URI + "/" + expected.getId()).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful())
        .andReturn()
        .getResponse()
        .getContentAsString();

    assertFalse(tagRuleRepository.existsById(expected.getId()));
  }

  @Test
  @WithMockUser(isAdmin = true)
  void deleteTagRule_WITH_unexisting_id() throws Exception {
    mvc.perform(delete(TAG_RULE_URI + "/" + "randomid").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  @Test
  @WithMockUser(isAdmin = true)
  void createTagRule_with_nonExistingTag() throws Exception {

    String assetGroupId = "assetGroupId";
    String tagName = "tagName";
    TagRuleInput input =
        TagRuleInput.builder().tagName(tagName).assetGroups(List.of(assetGroupId)).build();
    mvc.perform(
            post(TAG_RULE_URI)
                .content(asJsonString(input))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound());
  }

  @Test
  @WithMockUser(isAdmin = true)
  void createTagRule() throws Exception {

    AssetGroup assetGroup = createAssetGroup("assetGroupName");

    Tag tag = createTag("tagName");
    TagRuleInput input =
        TagRuleInput.builder()
            .tagName(tag.getName())
            .assetGroups(List.of(assetGroup.getId()))
            .build();

    String response =
        mvc.perform(
                post(TAG_RULE_URI)
                    .content(asJsonString(input))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    // -- ASSERT --
    assertNotNull(response);
    assertEquals(tag.getName(), JsonPath.read(response, "$.tag_name"));
    Map<String, Object> tagRuleAssetGroups = JsonPath.read(response, "$.asset_groups");
    assertTrue(tagRuleAssetGroups.containsKey(assetGroup.getId()));
  }

  @Test
  @WithMockUser(isAdmin = true)
  void updateTagRule() throws Exception {

    String assetGroupName1 = "assetGroupName1";
    String assetGroupName2 = "assetGroupName2";
    Tag tag = createTag("tagName");
    TagRule toUpdate = createTagRule(tag.getName(), List.of(assetGroupName1));
    AssetGroup assetGroup2 = createAssetGroup(assetGroupName2);

    TagRuleInput input =
        TagRuleInput.builder()
            .tagName(tag.getName())
            .assetGroups(List.of(assetGroup2.getId()))
            .build();

    String response =
        mvc.perform(
                put(TAG_RULE_URI + "/" + toUpdate.getId())
                    .content(asJsonString(input))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    // -- ASSERT --
    assertNotNull(response);
    assertEquals(tag.getName(), JsonPath.read(response, "$.tag_name"));
    Map<String, Object> tagRuleAssetGroups = JsonPath.read(response, "$.asset_groups");
    assertTrue(tagRuleAssetGroups.containsKey(assetGroup2.getId()));
  }

  @Test
  @WithMockUser(isAdmin = true)
  void updateTagRule_WITH_non_existing_tag() throws Exception {

    String assetGroupName1 = "assetGroupName1";
    String assetGroupName2 = "assetGroupName2";
    Tag tag = createTag("tagName");
    TagRule toUpdate = createTagRule(tag.getName(), List.of(assetGroupName1));
    AssetGroup assetGroup2 = createAssetGroup(assetGroupName2);

    TagRuleInput input =
        TagRuleInput.builder()
            .tagName("randomtagname")
            .assetGroups(List.of(assetGroup2.getId()))
            .build();

    mvc.perform(
            put(TAG_RULE_URI + "/" + toUpdate.getId())
                .content(asJsonString(input))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound());
  }

  @Test
  @WithMockUser(isAdmin = true)
  void updateTagRule_WITH_non_existing_id() throws Exception {

    String assetGroupName1 = "assetGroupName1";
    String assetGroupName2 = "assetGroupName2";
    Tag tag = createTag("tagName");
    createTagRule(tag.getName(), List.of(assetGroupName1));
    AssetGroup assetGroup2 = createAssetGroup(assetGroupName2);

    TagRuleInput input =
        TagRuleInput.builder()
            .tagName(tag.getName())
            .assetGroups(List.of(assetGroup2.getId()))
            .build();

    mvc.perform(
            put(TAG_RULE_URI + "/" + "randomid")
                .content(asJsonString(input))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound());
  }

  @Test
  @WithMockUser(isAdmin = true)
  void updateTagRule_WITH_non_existing_asset_group() throws Exception {

    String assetName1 = "assetGroupName1";
    Tag tag = createTag("tagName");
    TagRule toUpdate = createTagRule(tag.getName(), List.of(assetName1));

    TagRuleInput input =
        TagRuleInput.builder().tagName(tag.getName()).assetGroups(List.of("random")).build();

    mvc.perform(
            put(TAG_RULE_URI + "/" + toUpdate.getId())
                .content(asJsonString(input))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound());
  }

  @Test
  @WithMockUser(isAdmin = true)
  void findAllTagRules() throws Exception {
    TagRule tagRule1 = createTagRule("tag1", List.of("assetgroup1"));
    TagRule tagRule2 = createTagRule("tag2", List.of("assetgroup2"));
    String response =
        mvc.perform(
                get(TAG_RULE_URI)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertNotNull(response);
    List<Object> tagRuleList = JsonPath.read(response, "$");
    assertEquals(2, tagRuleList.size());
    assertEquals(tagRule1.getId(), JsonPath.read(response, "$[0].tag_rule_id"));
    assertEquals(tagRule2.getId(), JsonPath.read(response, "$[1].tag_rule_id"));
  }

  @Test
  @WithMockUser(isAdmin = true)
  void searchTagRule() throws Exception {
    createTagRule("tag1", List.of("assetgroup1"));
    createTagRule("tag2", List.of("assetgroup2"));
    createTagRule("tag3", List.of("assetgroup3"));
    createTagRule("tag4", List.of("assetgroup4"));

    SearchPaginationInput input = new SearchPaginationInput();
    input.setSize(2);
    input.setPage(0);

    String response =
        mvc.perform(
                post(TAG_RULE_URI + "/search")
                    .content(asJsonString(input))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    // -- ASSERT --
    assertNotNull(response);
    assertEquals(Integer.valueOf(2), JsonPath.read(response, "$.numberOfElements"));
    assertEquals(Integer.valueOf(4), JsonPath.read(response, "$.totalElements"));
  }

  private TagRule createTagRule(String tagName, List<String> assetGroupNames) {
    TagRule tagRule = new TagRule();
    tagRule.setId(tagName);
    tagRule.setTag(createTag(tagName));
    assetGroupNames.forEach(
        assetGroupName -> tagRule.getAssetGroups().add(createAssetGroup(assetGroupName)));
    return tagRuleRepository.save(tagRule);
  }

  private Tag createTag(String tagName) {
    Tag tag = new Tag();
    tag.setName(tagName + System.currentTimeMillis());
    tag.setColor("#0000");
    return tagRepository.save(tag);
  }

  private AssetGroup createAssetGroup(String assetGroupName) {
    AssetGroup assetGroup = AssetGroupFixture.createDefaultAssetGroup(assetGroupName);
    return assetGroupRepository.save(assetGroup);
  }
}
