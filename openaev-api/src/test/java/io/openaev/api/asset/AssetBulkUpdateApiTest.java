package io.openaev.api.asset;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.api.asset.dto.AssetBulkUpdateInput;
import io.openaev.context.TenantContext;
import io.openaev.database.model.AssetCategory;
import io.openaev.database.model.AssetCriticality;
import io.openaev.database.model.AssetSubCategory;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Filters;
import io.openaev.database.model.Tag;
import io.openaev.database.repository.EndpointRepository;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.fixtures.TagFixture;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.TagComposer;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/** {@code PUT /api/assets/bulk}: classification and tag addition over a bulk selection. */
@Transactional
@WithMockUser(withCapabilities = {Capability.MANAGE_ASSETS})
@DisplayName("Asset bulk update API")
class AssetBulkUpdateApiTest extends IntegrationTest {

  private static final String TENANT_ASSET_BULK_URI = "/api/tenants/{tenantId}/assets/bulk";

  @Autowired private MockMvc mvc;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private TagComposer tagComposer;
  @Autowired private EndpointRepository endpointRepository;
  @PersistenceContext private EntityManager entityManager;

  private String tenantId;
  private String prefix;
  private Endpoint first;
  private Endpoint second;
  private Endpoint other;

  @BeforeEach
  void setUp() {
    endpointComposer.reset();
    tagComposer.reset();
    tenantId = TenantContext.getCurrentTenant();
    String userId = testUserHolder.get().getId();
    tenantRepository.addUserToTenant(userId, tenantId);
    tenantMembershipCacheManager.evict(userId, tenantId);

    prefix = "bulk-" + UUID.randomUUID();
    first = persist(prefix + "-a");
    second = persist(prefix + "-b");
    other = persist("other-" + UUID.randomUUID());
    entityManager.flush();
    entityManager.clear();
  }

  @Test
  @DisplayName("given explicit ids, should set category, subcategory, url and criticality")
  void given_ids_should_updateClassification() throws Exception {
    AssetBulkUpdateInput input = new AssetBulkUpdateInput();
    input.setAssetIdsToProcess(List.of(first.getId(), second.getId()));
    input.setCategory(AssetCategory.WEB_APPLICATION);
    input.setSubcategory(AssetSubCategory.WEBSITE);
    input.setUrl("https://shop.example.com");
    input.setCriticality(AssetCriticality.HIGH);

    bulkUpdate(input).andExpect(status().is2xxSuccessful());

    for (Endpoint updated : reload(first, second)) {
      assertEquals(AssetCategory.WEB_APPLICATION, updated.getCategory());
      assertEquals(AssetSubCategory.WEBSITE, updated.getSubcategory());
      assertEquals("https://shop.example.com", updated.getUrl());
      assertEquals(AssetCriticality.HIGH, updated.getCriticality());
    }
    assertEquals(AssetCategory.HOST, reload(other).get(0).getCategory());
  }

  @Test
  @DisplayName("given a filter, should update only the matching assets and add the tag")
  void given_filter_should_updateMatchingAssetsAndAddTag() throws Exception {
    Tag tag =
        tagComposer
            .forTag(TagFixture.getTagWithText("discovered-" + UUID.randomUUID()))
            .persist()
            .get();
    entityManager.flush();

    AssetBulkUpdateInput input = new AssetBulkUpdateInput();
    input.setSearchPaginationInput(
        PaginationFixture.simpleSearchWithAndOperator(
            "asset_name", prefix, Filters.FilterOperator.starts_with));
    input.setCategory(AssetCategory.WEB_APPLICATION);
    input.setTagIdsToAdd(List.of(tag.getId()));

    bulkUpdate(input).andExpect(status().is2xxSuccessful());

    for (Endpoint updated : reload(first, second)) {
      assertEquals(AssetCategory.WEB_APPLICATION, updated.getCategory());
      // the HOST subcategory no longer fits WEB_APPLICATION: cleared, never left stale
      assertNull(updated.getSubcategory());
      assertTrue(updated.getTags().stream().anyMatch(t -> t.getId().equals(tag.getId())));
    }
    Endpoint untouched = reload(other).get(0);
    assertEquals(AssetCategory.HOST, untouched.getCategory());
    assertEquals(AssetSubCategory.SERVER, untouched.getSubcategory());
  }

  @Test
  @DisplayName("given only asset_internet_facing, should set then reset the flag on every asset")
  void given_onlyInternetFacing_should_setFlagOnEveryAsset() throws Exception {
    AssetBulkUpdateInput input = new AssetBulkUpdateInput();
    input.setAssetIdsToProcess(List.of(first.getId(), second.getId(), other.getId()));
    input.setInternetFacing(true);

    bulkUpdate(input).andExpect(status().is2xxSuccessful());

    for (Endpoint updated : reload(first, second, other)) {
      assertEquals(Boolean.TRUE, updated.getInternetFacing());
      // only the flag was given: the classification is left as it was
      assertEquals(AssetCategory.HOST, updated.getCategory());
      assertEquals(AssetSubCategory.SERVER, updated.getSubcategory());
    }

    input.setInternetFacing(false);
    bulkUpdate(input).andExpect(status().is2xxSuccessful());

    for (Endpoint updated : reload(first, second, other)) {
      assertEquals(Boolean.FALSE, updated.getInternetFacing());
    }
  }

  @Test
  @DisplayName("given no asset_internet_facing, should leave the flag unchanged")
  void given_noInternetFacing_should_leaveFlagUnchanged() throws Exception {
    AssetBulkUpdateInput setFlag = new AssetBulkUpdateInput();
    setFlag.setAssetIdsToProcess(List.of(first.getId()));
    setFlag.setInternetFacing(true);
    bulkUpdate(setFlag).andExpect(status().is2xxSuccessful());

    AssetBulkUpdateInput input = new AssetBulkUpdateInput();
    input.setAssetIdsToProcess(List.of(first.getId(), second.getId()));
    input.setCriticality(AssetCriticality.LOW);
    bulkUpdate(input).andExpect(status().is2xxSuccessful());

    List<Endpoint> updated = reload(first, second);
    assertEquals(Boolean.TRUE, updated.get(0).getInternetFacing());
    assertNull(updated.get(1).getInternetFacing());
  }

  @Test
  @DisplayName("given a subcategory of another category, should refuse with 400 and write nothing")
  void given_mismatchedSubcategory_should_badRequest() throws Exception {
    AssetBulkUpdateInput input = new AssetBulkUpdateInput();
    input.setAssetIdsToProcess(List.of(first.getId()));
    input.setCategory(AssetCategory.WEB_APPLICATION);
    input.setSubcategory(AssetSubCategory.SERVER);

    bulkUpdate(input).andExpect(status().isBadRequest());

    assertEquals(AssetCategory.HOST, reload(first).get(0).getCategory());
  }

  @Test
  @DisplayName("given a subcategory without category, should refuse with 400")
  void given_subcategoryWithoutCategory_should_badRequest() throws Exception {
    AssetBulkUpdateInput input = new AssetBulkUpdateInput();
    input.setAssetIdsToProcess(List.of(first.getId()));
    input.setSubcategory(AssetSubCategory.WEBSITE);

    bulkUpdate(input).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("given no attribute, should refuse with 400")
  void given_nothingToUpdate_should_badRequest() throws Exception {
    AssetBulkUpdateInput input = new AssetBulkUpdateInput();
    input.setAssetIdsToProcess(List.of(first.getId()));

    bulkUpdate(input).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("given an unknown tag, should report not found and write nothing")
  void given_unknownTag_should_notFound() throws Exception {
    AssetBulkUpdateInput input = new AssetBulkUpdateInput();
    input.setAssetIdsToProcess(List.of(first.getId()));
    input.setCategory(AssetCategory.WEB_APPLICATION);
    input.setTagIdsToAdd(List.of("does-not-exist"));

    bulkUpdate(input).andExpect(status().isNotFound());

    assertEquals(AssetCategory.HOST, reload(first).get(0).getCategory());
  }

  @Test
  @WithMockUser
  @DisplayName("given no asset write capability, should forbid")
  void given_noCapability_should_forbid() throws Exception {
    AssetBulkUpdateInput input = new AssetBulkUpdateInput();
    input.setAssetIdsToProcess(List.of(first.getId()));
    input.setCategory(AssetCategory.WEB_APPLICATION);

    bulkUpdate(input).andExpect(status().isForbidden());
  }

  // -- HELPERS --

  private Endpoint persist(String name) {
    Endpoint endpoint = EndpointFixture.createEndpoint(name);
    endpoint.setCategory(AssetCategory.HOST);
    endpoint.setSubcategory(AssetSubCategory.SERVER);
    return endpointComposer.forEndpoint(endpoint).persist().get();
  }

  private List<Endpoint> reload(Endpoint... endpoints) {
    entityManager.flush();
    entityManager.clear();
    return java.util.Arrays.stream(endpoints)
        .map(e -> endpointRepository.findById(e.getId()).orElseThrow())
        .toList();
  }

  private ResultActions bulkUpdate(AssetBulkUpdateInput input) throws Exception {
    return mvc.perform(
        put(TENANT_ASSET_BULK_URI, tenantId)
            .content(asJsonString(input))
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .with(csrf()));
  }
}
