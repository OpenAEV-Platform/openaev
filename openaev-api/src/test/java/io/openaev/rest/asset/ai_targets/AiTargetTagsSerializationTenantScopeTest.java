package io.openaev.rest.asset.ai_targets;

import static io.openaev.rest.asset.ai_targets.AiTargetApi.TENANT_AI_TARGET_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.AssetFixture.createDefaultAsset;
import static io.openaev.utils.fixtures.PaginationFixture.simpleTextSearch;
import static io.openaev.utils.fixtures.TagFixture.getTagWithText;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Asset;
import io.openaev.database.model.AssetCategory;
import io.openaev.database.model.Tag;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.AiTargetRepository;
import io.openaev.database.repository.AssetRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.rest.asset.ai_targets.form.AiTargetInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.composers.TagComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * With {@code tags} on v2 tenant isolation, the AI target endpoints return the raw {@code Asset}
 * entity and would serialize its lazy {@code asset_tags} open-in-view, AFTER the controller
 * transaction has committed and {@code app.current_tenants} is cleared: the tag lookup then fails
 * closed to an empty array. The edit form is filled from these responses, so saving an AI target
 * erased its tags. {@link AiTargetApi} loads the tags inside the scoped transaction to prevent it.
 * Tags are the only lazy association an AI target serializes ({@code assetGroups} is ignored).
 *
 * <p>The tests run with the production list of isolated tables, read from {@code
 * application.properties}, so they follow any later activation.
 *
 * <p>The class is deliberately NOT {@code @Transactional}: a rolled-back test transaction never
 * commits, so the tenant-local GUC would stay alive through serialization and MASK the very failure
 * this pins. Seeding, checks and cleanup therefore run in committed transactions opened through
 * {@link TenantScopedTransaction}, scoped to the test tenant.
 */
@WithMockUser(isAdmin = true)
@DisplayName("AI target endpoints serialize their tags with the production isolation")
class AiTargetTagsSerializationTenantScopeTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    Properties props = new Properties();
    try (InputStream in = new FileInputStream("src/main/resources/application.properties")) {
      props.load(in);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    String activeTables = props.getProperty("openaev.tenant.active-tables", "");
    registry.add("openaev.tenant.active-tables", () -> activeTables);
  }

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private TagComposer tagComposer;
  @Autowired private AiTargetRepository aiTargetRepository;
  @Autowired private AssetRepository assetRepository;
  @Autowired private TagRepository tagRepository;

  private String tenant;
  private final List<String> seededAiTargets = new ArrayList<>();

  @BeforeEach
  void setUp() throws Exception {
    tagComposer.reset();
    seededAiTargets.clear();
    // A tenant the current user is a member of, so the request scope covers the seeded rows.
    tenant = tenantHelper.createTenantWithCurrentUser("ai-target-tag-sink").getId();
  }

  @AfterEach
  void cleanup() {
    // The seeded rows were committed: delete them by id in a transaction scoped to the tenant,
    // then the tenant itself. The helper's deletes on collectors and collector_types (isolated in
    // production) join this transaction, so they run with the tenant in scope.
    inTenantScope(
        () -> {
          assetRepository.deleteAllById(seededAiTargets);
          tagRepository.deleteAllById(tagComposer.generatedItems.stream().map(Tag::getId).toList());
          tenantHelper.deleteCommittedTenants(tenant);
          return null;
        });
  }

  @Nested
  @DisplayName("List AI targets")
  class ListAiTargets {

    @Test
    @DisplayName(
        "given an AI target carrying tags when the AI targets are listed then the tags are"
            + " serialized")
    void
        given_an_ai_target_carrying_tags_when_the_ai_targets_are_listed_then_the_tags_are_serialized()
            throws Exception {
      // -- Arrange --
      Asset aiTarget = seedAiTarget(2);

      // -- Act & Assert --
      // Without the in-scope load the lazy asset_tags serializes as [] here: the post-commit
      // open-in-view load runs with app.current_tenants cleared and fails closed.
      listAiTargets()
          .andExpect(jsonPath("$[0].asset_tags", containsInAnyOrder(tagIds(aiTarget).toArray())));
    }
  }

  @Nested
  @DisplayName("Search AI targets")
  class SearchAiTargets {

    @Test
    @DisplayName(
        "given an AI target carrying tags when the AI targets are searched then the tags are"
            + " serialized")
    void
        given_an_ai_target_carrying_tags_when_the_ai_targets_are_searched_then_the_tags_are_serialized()
            throws Exception {
      // -- Arrange --
      Asset aiTarget = seedAiTarget(2);

      // -- Act & Assert --
      searchAiTargets(aiTarget)
          .andExpect(
              jsonPath("$.content[0].asset_tags", containsInAnyOrder(tagIds(aiTarget).toArray())));
    }

    @Test
    @DisplayName(
        "given an AI target with no tag when the AI targets are searched then its tags are"
            + " genuinely empty")
    void given_an_ai_target_with_no_tag_when_the_ai_targets_are_searched_then_its_tags_are_empty()
        throws Exception {
      // -- Arrange --
      Asset aiTarget = seedAiTarget(0);

      // -- Act & Assert --
      // Empty here proves the assertions above track the real links and are not always non-empty.
      searchAiTargets(aiTarget).andExpect(jsonPath("$.content[0].asset_tags", empty()));
    }
  }

  @Nested
  @DisplayName("Get an AI target")
  class GetAiTarget {

    @Test
    @DisplayName(
        "given an AI target carrying tags when it is read by id then the tags are serialized")
    void given_an_ai_target_carrying_tags_when_it_is_read_by_id_then_the_tags_are_serialized()
        throws Exception {
      // -- Arrange --
      Asset aiTarget = seedAiTarget(2);

      // -- Act & Assert --
      getAiTarget(aiTarget)
          .andExpect(jsonPath("$.asset_tags", containsInAnyOrder(tagIds(aiTarget).toArray())));
    }
  }

  @Nested
  @DisplayName("Edit an AI target")
  class EditAiTarget {

    @Test
    @DisplayName(
        "given a tagged AI target when a tag is added through the edit form then the existing tag"
            + " is kept")
    void
        given_a_tagged_ai_target_when_a_tag_is_added_through_the_edit_form_then_the_existing_tag_is_kept()
            throws Exception {
      // -- Arrange --
      Asset aiTarget = seedAiTarget(1);
      String addedTagId = inTenantScope(() -> newTag().persist().get().getId());
      List<String> editedTags = new ArrayList<>(tagIds(aiTarget));
      editedTags.add(addedTagId);

      // -- Act --
      // Same flow as the edit form: it is filled from the get-by-id response, then the new tag is
      // added. The response must therefore show the existing tag: an empty list here is what
      // erased it before the fix.
      getAiTarget(aiTarget)
          .andExpect(jsonPath("$.asset_tags", containsInAnyOrder(tagIds(aiTarget).toArray())));
      mvc.perform(
              put(TENANT_AI_TARGET_URI + "/{aiTargetId}", tenant, aiTarget.getId())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(editInput(aiTarget, editedTags)))
                  .with(csrf()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.asset_tags", containsInAnyOrder(editedTags.toArray())));

      // -- Assert --
      inTenantScope(
          () -> {
            Asset saved = aiTargetRepository.findAiTargetById(aiTarget.getId()).orElseThrow();
            assertThat(tagIds(saved)).containsExactlyInAnyOrderElementsOf(editedTags);
            return null;
          });
    }

    private AiTargetInput editInput(Asset aiTarget, List<String> tagIds) {
      AiTargetInput input = new AiTargetInput();
      input.setName(aiTarget.getName());
      input.setDescription(aiTarget.getDescription());
      input.setAiTargetProvider(aiTarget.getAiTargetProvider());
      input.setTagIds(tagIds);
      return input;
    }
  }

  // -- SEEDING --

  private Asset seedAiTarget(int tagCount) {
    Asset aiTarget = createDefaultAsset("ai-target-" + UUID.randomUUID());
    aiTarget.setTenant(new Tenant(tenant));
    aiTarget.setCategory(AssetCategory.AI_TARGET);
    aiTarget.setAiTargetProvider(Asset.AI_TARGET_PROVIDER.OPENAI_COMPATIBLE);
    Asset saved =
        inTenantScope(
            () -> {
              Set<Tag> tags = new HashSet<>();
              IntStream.range(0, tagCount).forEach(i -> tags.add(newTag().persist().get()));
              aiTarget.setTags(tags);
              return aiTargetRepository.save(aiTarget);
            });
    seededAiTargets.add(saved.getId());
    return saved;
  }

  private TagComposer.Composer newTag() {
    Tag tag = getTagWithText("ai-target-tag-" + UUID.randomUUID());
    tag.setTenant(new Tenant(tenant));
    return tagComposer.forTag(tag);
  }

  /** Runs the work in a committed transaction scoped to the test tenant. */
  private <T> T inTenantScope(Supplier<T> work) {
    return tenantTx.execute(TxCtx.forTenant(tenant), work);
  }

  // -- CALLS --

  /** Lists the AI targets under the test tenant's path: the fresh tenant holds only one. */
  private ResultActions listAiTargets() throws Exception {
    return mvc.perform(
            get(TENANT_AI_TARGET_URI, tenant).accept(MediaType.APPLICATION_JSON).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)));
  }

  /** Searches the AI target by its unique name under the test tenant's path. */
  private ResultActions searchAiTargets(Asset aiTarget) throws Exception {
    return mvc.perform(
            post(TENANT_AI_TARGET_URI + "/search", tenant)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(simpleTextSearch(aiTarget.getName())))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)));
  }

  private ResultActions getAiTarget(Asset aiTarget) throws Exception {
    return mvc.perform(
            get(TENANT_AI_TARGET_URI + "/{aiTargetId}", tenant, aiTarget.getId())
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().isOk());
  }

  private static List<String> tagIds(Asset aiTarget) {
    return aiTarget.getTags().stream().map(Tag::getId).toList();
  }
}
