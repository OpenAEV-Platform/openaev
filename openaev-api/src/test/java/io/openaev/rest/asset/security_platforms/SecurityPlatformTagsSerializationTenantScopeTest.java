package io.openaev.rest.asset.security_platforms;

import static io.openaev.rest.asset.security_platforms.SecurityPlatformApi.TENANT_SECURITY_PLATFORM_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.CollectorFixture.createDefaultCollector;
import static io.openaev.utils.fixtures.DocumentFixture.getDocumentJpeg;
import static io.openaev.utils.fixtures.InjectorFixture.createInjector;
import static io.openaev.utils.fixtures.PaginationFixture.simpleTextSearch;
import static io.openaev.utils.fixtures.SecurityPlatformFixture.createDefault;
import static io.openaev.utils.fixtures.TagFixture.getTagWithText;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
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
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Collector;
import io.openaev.database.model.Document;
import io.openaev.database.model.Injector;
import io.openaev.database.model.SecurityPlatform;
import io.openaev.database.model.Tag;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.DocumentRepository;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.database.repository.SecurityPlatformRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.rest.asset.security_platforms.form.SecurityPlatformInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.composers.CollectorComposer;
import io.openaev.utils.fixtures.composers.DocumentComposer;
import io.openaev.utils.fixtures.composers.SecurityPlatformComposer;
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
 * With {@code tags} on v2 tenant isolation, the security platform endpoints return the raw {@code
 * SecurityPlatform} entity and would serialize its lazy {@code asset_tags} open-in-view, AFTER the
 * controller transaction has committed and {@code app.current_tenants} is cleared: the tag lookup
 * then fails closed to an empty array. The edit form is filled from these responses, so saving a
 * security platform erased its tags. {@link SecurityPlatformApi} loads the tags inside the scoped
 * transaction to prevent it.
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
@DisplayName("Security platform endpoints serialize their tags with the production isolation")
class SecurityPlatformTagsSerializationTenantScopeTest extends IntegrationTest {

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
  @Autowired private SecurityPlatformComposer securityPlatformComposer;
  @Autowired private TagComposer tagComposer;
  @Autowired private SecurityPlatformRepository securityPlatformRepository;
  @Autowired private TagRepository tagRepository;
  @Autowired private CollectorComposer collectorComposer;
  @Autowired private DocumentComposer documentComposer;
  @Autowired private InjectorRepository injectorRepository;
  @Autowired private DocumentRepository documentRepository;

  private String tenant;
  private final List<Injector> seededInjectors = new ArrayList<>();

  @BeforeEach
  void setUp() throws Exception {
    securityPlatformComposer.reset();
    tagComposer.reset();
    collectorComposer.reset();
    documentComposer.reset();
    seededInjectors.clear();
    // A tenant the current user is a member of, so the request scope covers the seeded rows.
    tenant = tenantHelper.createTenantWithCurrentUser("security-platform-tag-sink").getId();
  }

  @AfterEach
  void cleanup() {
    // The composers' rows were committed: delete them by id in a transaction scoped to the tenant,
    // then the tenant itself. The helper's deletes on collectors and collector_types (isolated in
    // production) join this transaction, so they run with the tenant in scope.
    inTenantScope(
        () -> {
          injectorRepository.deleteAll(seededInjectors);
          securityPlatformRepository.deleteAllById(
              securityPlatformComposer.generatedItems.stream()
                  .map(SecurityPlatform::getId)
                  .toList());
          documentRepository.deleteAllById(
              documentComposer.generatedItems.stream().map(Document::getId).toList());
          tagRepository.deleteAllById(tagComposer.generatedItems.stream().map(Tag::getId).toList());
          // The seeded collector and its collector type go with the tenant's collectors below.
          tenantHelper.deleteCommittedTenants(tenant);
          return null;
        });
  }

  @Nested
  @DisplayName("List security platforms")
  class ListSecurityPlatforms {

    @Test
    @DisplayName(
        "given a security platform carrying every lazy association when the security platforms are listed then all"
            + " of them are serialized")
    void
        given_a_security_platform_carrying_every_lazy_association_when_the_security_platforms_are_listed_then_all_of_them_are_serialized()
            throws Exception {
      // -- Arrange --
      SeededSecurityPlatform seeded = seedSecurityPlatformWithEveryLazyAssociation();

      // -- Act & Assert --
      expectEveryLazyAssociation(listSecurityPlatforms(), "$[0]", seeded);
    }

    @Test
    @DisplayName(
        "given a security platform carrying tags when the security platforms are listed then the"
            + " tags are serialized")
    void
        given_a_security_platform_carrying_tags_when_the_security_platforms_are_listed_then_the_tags_are_serialized()
            throws Exception {
      // -- Arrange --
      SecurityPlatform securityPlatform = seedSecurityPlatform(2);

      // -- Act & Assert --
      // Without the in-scope load the lazy asset_tags serializes as [] here: the post-commit
      // open-in-view load runs with app.current_tenants cleared and fails closed.
      listSecurityPlatforms()
          .andExpect(
              jsonPath("$[0].asset_tags", containsInAnyOrder(tagIds(securityPlatform).toArray())));
    }
  }

  @Nested
  @DisplayName("Search security platforms")
  class SearchSecurityPlatforms {

    @Test
    @DisplayName(
        "given a security platform carrying every lazy association when the security platforms are searched then all"
            + " of them are serialized")
    void
        given_a_security_platform_carrying_every_lazy_association_when_the_security_platforms_are_searched_then_all_of_them_are_serialized()
            throws Exception {
      // -- Arrange --
      SeededSecurityPlatform seeded = seedSecurityPlatformWithEveryLazyAssociation();

      // -- Act & Assert --
      expectEveryLazyAssociation(
          searchSecurityPlatforms(seeded.platform()), "$.content[0]", seeded);
    }

    @Test
    @DisplayName(
        "given a security platform carrying tags when the security platforms are searched then the"
            + " tags are serialized")
    void
        given_a_security_platform_carrying_tags_when_the_security_platforms_are_searched_then_the_tags_are_serialized()
            throws Exception {
      // -- Arrange --
      SecurityPlatform securityPlatform = seedSecurityPlatform(2);

      // -- Act & Assert --
      searchSecurityPlatforms(securityPlatform)
          .andExpect(
              jsonPath(
                  "$.content[0].asset_tags",
                  containsInAnyOrder(tagIds(securityPlatform).toArray())));
    }

    @Test
    @DisplayName(
        "given a security platform with no tag when the security platforms are searched then its"
            + " tags are genuinely empty")
    void
        given_a_security_platform_with_no_tag_when_the_security_platforms_are_searched_then_its_tags_are_empty()
            throws Exception {
      // -- Arrange --
      SecurityPlatform securityPlatform = seedSecurityPlatform(0);

      // -- Act & Assert --
      // Empty here proves the assertions above track the real links and are not always non-empty.
      searchSecurityPlatforms(securityPlatform)
          .andExpect(jsonPath("$.content[0].asset_tags", empty()));
    }
  }

  @Nested
  @DisplayName("Get a security platform")
  class GetSecurityPlatform {

    @Test
    @DisplayName(
        "given a security platform carrying every lazy association when it is read by id then all"
            + " of them are serialized")
    void
        given_a_security_platform_carrying_every_lazy_association_when_it_is_read_by_id_then_all_of_them_are_serialized()
            throws Exception {
      // -- Arrange --
      SeededSecurityPlatform seeded = seedSecurityPlatformWithEveryLazyAssociation();

      // -- Act & Assert --
      expectEveryLazyAssociation(getSecurityPlatform(seeded.platform()), "$", seeded);
    }

    @Test
    @DisplayName(
        "given a security platform carrying tags when it is read by id then the tags are"
            + " serialized")
    void
        given_a_security_platform_carrying_tags_when_it_is_read_by_id_then_the_tags_are_serialized()
            throws Exception {
      // -- Arrange --
      SecurityPlatform securityPlatform = seedSecurityPlatform(2);

      // -- Act & Assert --
      getSecurityPlatform(securityPlatform)
          .andExpect(
              jsonPath("$.asset_tags", containsInAnyOrder(tagIds(securityPlatform).toArray())));
    }
  }

  @Nested
  @DisplayName("Edit a security platform")
  class EditSecurityPlatform {

    @Test
    @DisplayName(
        "given a security platform carrying every lazy association when a tag is added through the"
            + " edit form then nothing existing is lost")
    void
        given_a_security_platform_carrying_every_lazy_association_when_a_tag_is_added_through_the_edit_form_then_nothing_existing_is_lost()
            throws Exception {
      // -- Arrange --
      SeededSecurityPlatform seeded = seedSecurityPlatformWithEveryLazyAssociation();
      SecurityPlatform securityPlatform = seeded.platform();
      String addedTagId = inTenantScope(() -> newTag().persist().get().getId());
      List<String> editedTags = new ArrayList<>(tagIds(securityPlatform));
      editedTags.add(addedTagId);

      // -- Act --
      // Same flow as the edit form: it is filled from the list, search or get-by-id response,
      // then the new tag is added. The response must therefore show every existing link: an empty
      // tag list here is what erased the tags before the fix.
      expectEveryLazyAssociation(getSecurityPlatform(securityPlatform), "$", seeded);
      ResultActions updated =
          mvc.perform(
                  put(
                          TENANT_SECURITY_PLATFORM_URI + "/{securityPlatformId}",
                          tenant,
                          securityPlatform.getId())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(editInput(securityPlatform, editedTags)))
                      .with(csrf()))
              .andExpect(status().isOk());

      // -- Assert --
      // The UI replaces its local state with the update response, so it must carry every link too.
      updated
          .andExpect(jsonPath("$.asset_tags", containsInAnyOrder(editedTags.toArray())))
          .andExpect(jsonPath("$.security_platform_collectors", contains(seeded.collectorId())))
          .andExpect(jsonPath("$.security_platform_injectors", contains(seeded.injectorId())))
          .andExpect(
              jsonPath("$.security_platform_logo_light").value(logoLightId(securityPlatform)))
          .andExpect(jsonPath("$.security_platform_logo_dark").value(logoDarkId(securityPlatform)));
      inTenantScope(
          () -> {
            SecurityPlatform saved =
                securityPlatformRepository.findById(securityPlatform.getId()).orElseThrow();
            assertThat(tagIds(saved)).containsExactlyInAnyOrderElementsOf(editedTags);
            assertThat(saved.getLogoLight().getId()).isEqualTo(logoLightId(securityPlatform));
            assertThat(saved.getLogoDark().getId()).isEqualTo(logoDarkId(securityPlatform));
            return null;
          });
    }

    private SecurityPlatformInput editInput(
        SecurityPlatform securityPlatform, List<String> tagIds) {
      SecurityPlatformInput input = new SecurityPlatformInput();
      input.setName(securityPlatform.getName());
      input.setDescription(securityPlatform.getDescription());
      input.setSecurityPlatformType(securityPlatform.getSecurityPlatformType());
      input.setTagIds(tagIds);
      input.setLogoLight(logoLightId(securityPlatform));
      input.setLogoDark(logoDarkId(securityPlatform));
      return input;
    }
  }

  // -- SEEDING --

  private SecurityPlatform seedSecurityPlatform(int tagCount) {
    SecurityPlatform securityPlatform =
        createDefault("security-platform-" + UUID.randomUUID(), "EDR");
    securityPlatform.setTenant(new Tenant(tenant));
    return inTenantScope(
        () -> {
          Set<Tag> tags = new HashSet<>();
          IntStream.range(0, tagCount).forEach(i -> tags.add(newTag().persist().get()));
          securityPlatform.setTags(tags);
          return securityPlatformComposer.forSecurityPlatform(securityPlatform).persist().get();
        });
  }

  /**
   * A security platform carrying every lazy association it serializes: tags, a managing collector,
   * a registering injector and both logos. {@code security_platform_traces} is left out: it has no
   * custom serializer, so an uninitialized trace list serializes the same way whatever the tenant
   * scope.
   */
  private record SeededSecurityPlatform(
      SecurityPlatform platform, String collectorId, String injectorId) {}

  private SeededSecurityPlatform seedSecurityPlatformWithEveryLazyAssociation() {
    SecurityPlatform securityPlatform =
        createDefault("security-platform-" + UUID.randomUUID(), "EDR");
    securityPlatform.setTenant(new Tenant(tenant));
    return inTenantScope(
        () -> {
          securityPlatform.setTags(new HashSet<>(Set.of(newTag().persist().get())));
          securityPlatform.setLogoLight(newDocument().persist().get());
          securityPlatform.setLogoDark(newDocument().persist().get());
          Collector collector = createDefaultCollector("collector-" + UUID.randomUUID());
          collector.setTenantId(tenant);
          collectorComposer
              .forCollector(collector)
              .withSecurityPlatform(securityPlatformComposer.forSecurityPlatform(securityPlatform))
              .persist();
          String injectorId = UUID.randomUUID().toString();
          Injector injector =
              createInjector(injectorId, "injector-" + injectorId, "injector-type-" + injectorId);
          injector.setTenantId(tenant);
          injector.setSecurityPlatform(securityPlatform);
          seededInjectors.add(injectorRepository.save(injector));
          return new SeededSecurityPlatform(securityPlatform, collector.getId(), injectorId);
        });
  }

  private DocumentComposer.Composer newDocument() {
    Document document = getDocumentJpeg();
    document.setName("security-platform-logo-" + UUID.randomUUID());
    document.setTarget(document.getName());
    document.setTenant(new Tenant(tenant));
    return documentComposer.forDocument(document);
  }

  private TagComposer.Composer newTag() {
    Tag tag = getTagWithText("security-platform-tag-" + UUID.randomUUID());
    tag.setTenant(new Tenant(tenant));
    return tagComposer.forTag(tag);
  }

  /**
   * Runs the work in a committed transaction scoped to the test tenant. The composers still read
   * the v1 thread-local tenant to attribute some rows (collector types), so it is set too.
   */
  private <T> T inTenantScope(Supplier<T> work) {
    TenantContext.setCurrentTenant(tenant);
    try {
      return tenantTx.execute(TxCtx.forTenant(tenant), work);
    } finally {
      TenantContext.clearCurrentTenant();
    }
  }

  // -- CALLS --

  /** Lists the security platforms under the test tenant's path: the fresh tenant holds only one. */
  private ResultActions listSecurityPlatforms() throws Exception {
    return mvc.perform(
            get(TENANT_SECURITY_PLATFORM_URI, tenant)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)));
  }

  /** Searches the security platform by its unique name under the test tenant's path. */
  private ResultActions searchSecurityPlatforms(SecurityPlatform securityPlatform)
      throws Exception {
    return mvc.perform(
            post(TENANT_SECURITY_PLATFORM_URI + "/search", tenant)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(simpleTextSearch(securityPlatform.getName())))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)));
  }

  private ResultActions getSecurityPlatform(SecurityPlatform securityPlatform) throws Exception {
    return mvc.perform(
            get(
                    TENANT_SECURITY_PLATFORM_URI + "/{securityPlatformId}",
                    tenant,
                    securityPlatform.getId())
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().isOk());
  }

  private static void expectEveryLazyAssociation(
      ResultActions response, String platformPath, SeededSecurityPlatform seeded) throws Exception {
    SecurityPlatform platform = seeded.platform();
    response
        .andExpect(
            jsonPath(platformPath + ".asset_tags", containsInAnyOrder(tagIds(platform).toArray())))
        .andExpect(
            jsonPath(
                platformPath + ".security_platform_collectors", contains(seeded.collectorId())))
        .andExpect(
            jsonPath(platformPath + ".security_platform_injectors", contains(seeded.injectorId())))
        .andExpect(
            jsonPath(platformPath + ".security_platform_logo_light").value(logoLightId(platform)))
        .andExpect(
            jsonPath(platformPath + ".security_platform_logo_dark").value(logoDarkId(platform)));
  }

  private static String logoLightId(SecurityPlatform securityPlatform) {
    return securityPlatform.getLogoLight().getId();
  }

  private static String logoDarkId(SecurityPlatform securityPlatform) {
    return securityPlatform.getLogoDark().getId();
  }

  private static List<String> tagIds(SecurityPlatform securityPlatform) {
    return securityPlatform.getTags().stream().map(Tag::getId).toList();
  }
}
