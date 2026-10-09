package io.openaev.rest.organization;

import static io.openaev.rest.organization.OrganizationApi.TENANT_ORGANIZATION_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.OrganizationFixture.createDefaultOrganisation;
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
import io.openaev.database.model.Organization;
import io.openaev.database.model.Tag;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.OrganizationRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.rest.organization.form.OrganizationUpdateInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.composers.OrganizationComposer;
import io.openaev.utils.fixtures.composers.TagComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
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
 * With {@code tags} on v2 tenant isolation, the organization search and get-by-id endpoints return
 * the raw {@code Organization} entity and would serialize its lazy {@code organization_tags}
 * open-in-view, AFTER the controller transaction has committed and {@code app.current_tenants} is
 * cleared: the tag lookup then fails closed to an empty array. The edit form is filled from these
 * responses, so saving an organization erased its tags. {@link
 * io.openaev.service.organization.OrganizationService} loads the tags inside the scoped transaction
 * to prevent it.
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
@DisplayName("Organization endpoints serialize their tags with the production isolation")
class OrganizationTagsSerializationTenantScopeTest extends IntegrationTest {

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
  @Autowired private OrganizationComposer organizationComposer;
  @Autowired private TagComposer tagComposer;
  @Autowired private OrganizationRepository organizationRepository;
  @Autowired private TagRepository tagRepository;

  private String tenant;

  @BeforeEach
  void setUp() throws Exception {
    organizationComposer.reset();
    tagComposer.reset();
    // A tenant the current user is a member of, so the request scope covers the seeded rows.
    tenant = tenantHelper.createTenantWithCurrentUser("organization-tag-sink").getId();
  }

  @AfterEach
  void cleanup() {
    // The composers' rows were committed: delete them by id in a transaction scoped to the tenant,
    // then the tenant itself. The helper's deletes on collectors and collector_types (isolated in
    // production) join this transaction, so they run with the tenant in scope.
    inTenantScope(
        () -> {
          organizationRepository.deleteAllById(
              organizationComposer.generatedItems.stream().map(Organization::getId).toList());
          tagRepository.deleteAllById(tagComposer.generatedItems.stream().map(Tag::getId).toList());
          tenantHelper.deleteCommittedTenants(tenant);
          return null;
        });
  }

  @Nested
  @DisplayName("Search organizations")
  class SearchOrganizations {

    @Test
    @DisplayName(
        "given an organization carrying tags when the organizations are searched then the tags are"
            + " serialized")
    void
        given_an_organization_carrying_tags_when_the_organizations_are_searched_then_the_tags_are_serialized()
            throws Exception {
      // -- Arrange --
      Organization organization = seedOrganization(o -> o.withTag(newTag()).withTag(newTag()));

      // -- Act & Assert --
      // Without the in-scope load the lazy organization_tags serializes as [] here: the
      // post-commit open-in-view load runs with app.current_tenants cleared and fails closed.
      searchOrganizations(organization)
          .andExpect(
              jsonPath(
                  "$.content[0].organization_tags",
                  containsInAnyOrder(tagIds(organization).toArray())));
    }

    @Test
    @DisplayName(
        "given an organization with no tag when the organizations are searched then its tags are"
            + " genuinely empty")
    void
        given_an_organization_with_no_tag_when_the_organizations_are_searched_then_its_tags_are_empty()
            throws Exception {
      // -- Arrange --
      Organization organization = seedOrganization(UnaryOperator.identity());

      // -- Act & Assert --
      // Empty here proves the assertion above tracks the real links and is not always non-empty.
      searchOrganizations(organization)
          .andExpect(jsonPath("$.content[0].organization_tags", empty()));
    }
  }

  @Nested
  @DisplayName("Get an organization")
  class GetOrganization {

    @Test
    @DisplayName(
        "given an organization carrying tags when it is read by id then the tags are serialized")
    void given_an_organization_carrying_tags_when_it_is_read_by_id_then_the_tags_are_serialized()
        throws Exception {
      // -- Arrange --
      Organization organization = seedOrganization(o -> o.withTag(newTag()).withTag(newTag()));

      // -- Act & Assert --
      getOrganization(organization)
          .andExpect(
              jsonPath("$.organization_tags", containsInAnyOrder(tagIds(organization).toArray())));
    }
  }

  @Nested
  @DisplayName("Edit an organization")
  class EditOrganization {

    @Test
    @DisplayName(
        "given a tagged organization when a tag is added through the edit form then the existing"
            + " tag is kept")
    void
        given_a_tagged_organization_when_a_tag_is_added_through_the_edit_form_then_the_existing_tag_is_kept()
            throws Exception {
      // -- Arrange --
      Organization organization = seedOrganization(o -> o.withTag(newTag()));
      String addedTagId = inTenantScope(() -> newTag().persist().get().getId());
      List<String> editedTags = new ArrayList<>(tagIds(organization));
      editedTags.add(addedTagId);

      // -- Act --
      // Same flow as the edit form: it is filled from the search or the get-by-id response, then
      // the new tag is added. Both responses must therefore show the existing tag: an empty list
      // here is what erased it before the fix.
      searchOrganizations(organization)
          .andExpect(
              jsonPath(
                  "$.content[0].organization_tags",
                  containsInAnyOrder(tagIds(organization).toArray())));
      getOrganization(organization)
          .andExpect(
              jsonPath("$.organization_tags", containsInAnyOrder(tagIds(organization).toArray())));
      mvc.perform(
              put(TENANT_ORGANIZATION_URI + "/{organizationId}", tenant, organization.getId())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(editInput(organization, editedTags)))
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- Assert --
      inTenantScope(
          () -> {
            Organization saved =
                organizationRepository.findById(organization.getId()).orElseThrow();
            assertThat(tagIds(saved)).containsExactlyInAnyOrderElementsOf(editedTags);
            return null;
          });
    }

    private OrganizationUpdateInput editInput(Organization organization, List<String> tagIds) {
      OrganizationUpdateInput input = new OrganizationUpdateInput();
      input.setName(organization.getName());
      input.setDescription(organization.getDescription());
      input.setTagIds(tagIds);
      return input;
    }
  }

  // -- SEEDING --

  private Organization seedOrganization(UnaryOperator<OrganizationComposer.Composer> withTags) {
    Organization organization = createDefaultOrganisation();
    organization.setTenant(new Tenant(tenant));
    return inTenantScope(
        () -> withTags.apply(organizationComposer.forOrganization(organization)).persist().get());
  }

  private TagComposer.Composer newTag() {
    Tag tag = getTagWithText("organization-tag-" + UUID.randomUUID());
    tag.setTenant(new Tenant(tenant));
    return tagComposer.forTag(tag);
  }

  /** Runs the work in a committed transaction scoped to the test tenant. */
  private <T> T inTenantScope(Supplier<T> work) {
    return tenantTx.execute(TxCtx.forTenant(tenant), work);
  }

  // -- CALLS --

  /**
   * Searches the organization by its unique name under the test tenant's path. The tenant
   * onboarding seeds its own organization, so the search is narrowed to the one under test.
   */
  private ResultActions searchOrganizations(Organization organization) throws Exception {
    return mvc.perform(
            post(TENANT_ORGANIZATION_URI + "/search", tenant)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(simpleTextSearch(organization.getName())))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)));
  }

  private ResultActions getOrganization(Organization organization) throws Exception {
    return mvc.perform(
            get(TENANT_ORGANIZATION_URI + "/{organizationId}", tenant, organization.getId())
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().isOk());
  }

  private static List<String> tagIds(Organization organization) {
    return organization.getTags().stream().map(Tag::getId).toList();
  }
}
