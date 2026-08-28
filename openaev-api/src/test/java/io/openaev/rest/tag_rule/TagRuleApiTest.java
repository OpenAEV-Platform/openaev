package io.openaev.rest.tag_rule;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.Tag;
import io.openaev.database.model.TagRule;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.AssetGroupRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.database.repository.TagRuleRepository;
import io.openaev.rest.tag_rule.form.TagRuleInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.AssetGroupFixture;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.openaev.utilstest.RabbitMQTestListener;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@TestInstance(PER_CLASS)
@Transactional
public class TagRuleApiTest extends IntegrationTest {

  public static final String TAG_RULE_URI = "/api/tag-rules";

  @Autowired private MockMvc mvc;

  @Autowired private AssetGroupRepository assetGroupRepository;
  @Autowired private TagRepository tagRepository;
  @Autowired private TagRuleRepository tagRuleRepository;
  @Autowired private TenantIsolationTestHelper tenantIsolationHelper;

  @BeforeEach
  public void setup() {
    // We remove any tagrule existing by default
    tagRuleRepository.deleteAll();
  }

  @Test
  @WithMockUser(isAdmin = true)
  void findTagRule() throws Exception {
    String assetGroupName = "assetGroupName";
    String tagId = "tagName";
    TagRule expected = createTagRule(tagId, List.of(assetGroupName));

    String response =
        mvc.perform(
                get(TAG_RULE_URI + "/" + expected.getId())
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
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
    String tagName = "tagName";
    TagRule expected = createTagRule(tagName, List.of(assetGroupName));
    mvc.perform(
            delete(TAG_RULE_URI + "/" + expected.getId())
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful())
        .andReturn()
        .getResponse()
        .getContentAsString();

    assertFalse(tagRuleRepository.existsById(expected.getId()));
  }

  @Test
  @WithMockUser(isAdmin = true)
  void deleteTagRule_WITH_unexisting_id() throws Exception {
    mvc.perform(
            delete(TAG_RULE_URI + "/" + "randomid").accept(MediaType.APPLICATION_JSON).with(csrf()))
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
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
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
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
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
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
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
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
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
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
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
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
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
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
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
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
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

  // -- TENANT ISOLATION TESTS --

  @Nested
  @DisplayName("Tenant Isolation")
  @Transactional
  @WithMockUser(isAdmin = true)
  class TenantIsolation {

    private TagRule createTagRuleInTenant(String tenantId) {
      tenantIsolationHelper.switchToTenant(tenantId, entityManager);
      TagRule tagRule = new TagRule();
      Tag tag = new Tag();
      tag.setName("isolation-tag-" + System.currentTimeMillis());
      tag.setColor("#0000");
      tag = tagRepository.save(tag);
      tagRule.setTag(tag);
      AssetGroup ag = AssetGroupFixture.createDefaultAssetGroup("isolation-ag");
      ag = assetGroupRepository.save(ag);
      tagRule.getAssetGroups().add(ag);
      return tagRuleRepository.save(tagRule);
    }

    @Test
    @DisplayName("TagRule created in tenant X should NOT be readable from tenant Y")
    void given_tagRuleInTenantX_should_notBeReadableFromTenantY() throws Exception {
      // Arrange
      Tenant tenantX = tenantIsolationHelper.createTenantWithCurrentUser("Tenant X");
      Tenant tenantY = tenantIsolationHelper.createTenantWithCurrentUser("Tenant Y");

      TagRule rule = createTagRuleInTenant(tenantX.getId());

      entityManager.flush();
      entityManager.clear();

      // Act — read from tenant Y
      String response =
          mvc.perform(
                  get("/api/tenants/" + tenantY.getId() + "/tag-rules/" + rule.getId())
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert — should return empty (findById returns null via .orElse(null))
      assertEquals("", response);
    }

    @Test
    @DisplayName("TagRule created in tenant X should be readable from tenant X")
    void given_tagRuleInTenantX_should_beReadableFromTenantX() throws Exception {
      // Arrange
      Tenant tenantX = tenantIsolationHelper.createTenantWithCurrentUser("Tenant X");

      TagRule rule = createTagRuleInTenant(tenantX.getId());

      entityManager.flush();
      entityManager.clear();

      // Act & Assert — read from same tenant should succeed
      String response =
          mvc.perform(
                  get("/api/tenants/" + tenantX.getId() + "/tag-rules/" + rule.getId())
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertNotNull(JsonPath.read(response, "$.tag_rule_id"));
    }

    @Test
    @DisplayName("TagRule created in tenant X should NOT be updatable from tenant Y")
    void given_tagRuleInTenantX_should_notBeUpdatableFromTenantY() throws Exception {
      // Arrange
      Tenant tenantX = tenantIsolationHelper.createTenantWithCurrentUser("Tenant X");
      Tenant tenantY = tenantIsolationHelper.createTenantWithCurrentUser("Tenant Y");

      TagRule rule = createTagRuleInTenant(tenantX.getId());

      // Create a tag in tenant Y for the update input
      tenantIsolationHelper.switchToTenant(tenantY.getId(), entityManager);
      Tag tagY = new Tag();
      tagY.setName("tag-y-" + System.currentTimeMillis());
      tagY.setColor("#1111");
      tagY = tagRepository.save(tagY);

      entityManager.flush();
      entityManager.clear();

      // Act — update from tenant Y
      TagRuleInput updateInput =
          TagRuleInput.builder().tagName(tagY.getName()).assetGroups(List.of()).build();

      int responseStatus =
          mvc.perform(
                  put("/api/tenants/" + tenantY.getId() + "/tag-rules/" + rule.getId())
                      .content(asJsonString(updateInput))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andReturn()
              .getResponse()
              .getStatus();

      // Assert
      assertThat(responseStatus).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    @DisplayName("TagRule created in tenant X should NOT be listed from tenant Y")
    void given_tagRuleInTenantX_should_notBeListedFromTenantY() throws Exception {
      // Arrange
      Tenant tenantX = tenantIsolationHelper.createTenantWithCurrentUser("Tenant X");
      Tenant tenantY = tenantIsolationHelper.createTenantWithCurrentUser("Tenant Y");

      TagRule rule = createTagRuleInTenant(tenantX.getId());

      entityManager.flush();
      entityManager.clear();

      // Act — list from tenant Y
      String response =
          mvc.perform(
                  get("/api/tenants/" + tenantY.getId() + "/tag-rules")
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert — tag rule from tenant X must not appear
      List<String> ids = JsonPath.read(response, "$[*].tag_rule_id");
      assertFalse(ids.contains(rule.getId()));
    }

    @Test
    @DisplayName("TagRule created in tenant X should NOT be deletable from tenant Y")
    void given_tagRuleInTenantX_should_notBeDeletableFromTenantY() throws Exception {
      // Arrange
      Tenant tenantX = tenantIsolationHelper.createTenantWithCurrentUser("Tenant X");
      Tenant tenantY = tenantIsolationHelper.createTenantWithCurrentUser("Tenant Y");

      TagRule rule = createTagRuleInTenant(tenantX.getId());

      entityManager.flush();
      entityManager.clear();

      // Act — delete from tenant Y
      int responseStatus =
          mvc.perform(
                  delete("/api/tenants/" + tenantY.getId() + "/tag-rules/" + rule.getId())
                      .with(csrf()))
              .andReturn()
              .getResponse()
              .getStatus();

      // Assert
      assertThat(responseStatus).isEqualTo(HttpStatus.NOT_FOUND.value());
    }
  }
}
