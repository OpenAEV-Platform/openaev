package io.openaev.rest.asset.security_platforms;

import static io.openaev.rest.asset.security_platforms.SecurityPlatformApi.TENANT_SECURITY_PLATFORM_URI;
import static io.openaev.utils.fixtures.TagFixture.getTagWithText;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Injector;
import io.openaev.database.model.SecurityPlatform;
import io.openaev.database.model.Tag;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.database.repository.SecurityPlatformRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.fixtures.SecurityPlatformFixture;
import io.openaev.utils.fixtures.composers.SecurityPlatformComposer;
import io.openaev.utils.fixtures.composers.TagComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.StreamSupport;
import javax.sql.DataSource;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The security platform endpoints return the raw {@code SecurityPlatform} entity. Its lazy {@code
 * asset_tags} and {@code security_platform_injectors}, serialized open-in-view after the scoped
 * transaction has committed, fail closed to an empty array. The edit form is filled from the read
 * responses, so saving it unchanged replaced the tags with nothing. Each test reads a platform,
 * sends the response back as it came, and checks the links in the database.
 *
 * <p>The tests run with the production list of isolated tables. The class is deliberately NOT
 * {@code @Transactional}: a rolled-back test transaction never commits, so the tenant-local scope
 * would stay alive through serialization and mask the failure. Seeding and cleanup run in committed
 * transactions scoped to the test tenant, and the database checks read the link columns directly,
 * outside any scope.
 */
@WithMockUser(isAdmin = true)
@DisplayName("Security platform endpoints keep their links across a read and an unchanged save")
class SecurityPlatformLinksRoundTripTenantScopeTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper mapper;
  @Autowired private DataSource dataSource;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private SecurityPlatformComposer securityPlatformComposer;
  @Autowired private TagComposer tagComposer;
  @Autowired private SecurityPlatformRepository securityPlatformRepository;
  @Autowired private TagRepository tagRepository;
  @Autowired private InjectorRepository injectorRepository;

  private JdbcTemplate jdbc;
  private String tenant;
  private final List<String> seededInjectors = new ArrayList<>();

  @BeforeEach
  void setUp() throws Exception {
    securityPlatformComposer.reset();
    tagComposer.reset();
    seededInjectors.clear();
    jdbc = new JdbcTemplate(dataSource);
    // A tenant the current user is a member of, so the request scope covers the seeded rows.
    tenant = tenantHelper.createTenantWithCurrentUser("security-platform-round-trip").getId();
  }

  @AfterEach
  void cleanup() {
    seededInjectors.forEach(id -> jdbc.update("DELETE FROM injectors WHERE injector_id = ?", id));
    inTenantScope(
        () -> {
          securityPlatformRepository.deleteAllById(
              securityPlatformComposer.generatedItems.stream()
                  .map(SecurityPlatform::getId)
                  .toList());
          tagRepository.deleteAllById(tagComposer.generatedItems.stream().map(Tag::getId).toList());
          // The helper's deletes on collectors and collector_types (isolated in production) join
          // this transaction, so they run with the tenant in scope.
          tenantHelper.deleteCommittedTenants(tenant);
          return null;
        });
  }

  @Nested
  @DisplayName("Read a security platform by id")
  class ReadById {

    @Test
    @DisplayName("given a tagged platform should keep its tags when the read platform is saved")
    void given_taggedPlatform_should_keepItsTags_when_readPlatformIsSaved() throws Exception {
      // -- Arrange --
      SecurityPlatform platform = seedPlatform(null);
      List<String> seededTags = tagIds(platform);
      SoftAssertions softly = new SoftAssertions();

      // -- Act --
      JsonNode read =
          readJson(
              mvc.perform(
                  get(
                      TENANT_SECURITY_PLATFORM_URI + "/{securityPlatformId}",
                      tenant,
                      platform.getId())));
      softly
          .assertThat(ids(read.get("asset_tags")))
          .as("tags in the read response")
          .containsExactlyInAnyOrderElementsOf(seededTags);
      saveUnchanged(platform, read);

      // -- Assert --
      softly
          .assertThat(tagLinksInDatabase(platform))
          .as("tag links after saving the read platform")
          .containsExactlyInAnyOrderElementsOf(seededTags);
      softly.assertAll();
    }
  }

  @Nested
  @DisplayName("List and search security platforms")
  class ListAndSearch {

    @Test
    @DisplayName("given a tagged platform should keep its tags when the listed platform is saved")
    void given_taggedPlatform_should_keepItsTags_when_listedPlatformIsSaved() throws Exception {
      // -- Arrange --
      SecurityPlatform platform = seedPlatform(null);
      List<String> seededTags = tagIds(platform);
      SoftAssertions softly = new SoftAssertions();

      // -- Act --
      JsonNode listed =
          findById(
              readJson(mvc.perform(get(TENANT_SECURITY_PLATFORM_URI, tenant))), platform.getId());
      softly
          .assertThat(ids(listed.get("asset_tags")))
          .as("tags in the list response")
          .containsExactlyInAnyOrderElementsOf(seededTags);
      saveUnchanged(platform, listed);

      // -- Assert --
      softly
          .assertThat(tagLinksInDatabase(platform))
          .as("tag links after saving the listed platform")
          .containsExactlyInAnyOrderElementsOf(seededTags);
      softly.assertAll();
    }

    @Test
    @DisplayName("given a tagged platform should keep its tags when the searched platform is saved")
    void given_taggedPlatform_should_keepItsTags_when_searchedPlatformIsSaved() throws Exception {
      // -- Arrange --
      SecurityPlatform platform = seedPlatform(null);
      List<String> seededTags = tagIds(platform);
      SoftAssertions softly = new SoftAssertions();

      // -- Act --
      JsonNode searched =
          findById(
              readJson(
                      mvc.perform(
                          post(TENANT_SECURITY_PLATFORM_URI + "/search", tenant)
                              .contentType(MediaType.APPLICATION_JSON)
                              .content(
                                  mapper.writeValueAsString(
                                      PaginationFixture.simpleTextSearch(platform.getName())))
                              .with(csrf())))
                  .get("content"),
              platform.getId());
      softly
          .assertThat(ids(searched.get("asset_tags")))
          .as("tags in the search response")
          .containsExactlyInAnyOrderElementsOf(seededTags);
      saveUnchanged(platform, searched);

      // -- Assert --
      softly
          .assertThat(tagLinksInDatabase(platform))
          .as("tag links after saving the searched platform")
          .containsExactlyInAnyOrderElementsOf(seededTags);
      softly.assertAll();
    }
  }

  @Nested
  @DisplayName("Register a security platform through the upsert")
  class Upsert {

    @Test
    @DisplayName(
        "given a platform managed by an injector should return the injector link and keep it when"
            + " the response is upserted again")
    void given_platformManagedByInjector_should_returnAndKeepInjectorLink_when_upsertedAgain()
        throws Exception {
      // -- Arrange --
      String injectorType = "round-trip-injector-" + UUID.randomUUID();
      SecurityPlatform platform = seedPlatform(injectorType);
      String injectorId = seedInjectorManaging(platform, injectorType);
      List<String> seededTags = tagIds(platform);
      SoftAssertions softly = new SoftAssertions();

      // -- Act --
      JsonNode registered = upsert(registrationOf(platform, seededTags));
      softly
          .assertThat(ids(registered.get("security_platform_injectors")))
          .as("injectors in the upsert response")
          .containsExactly(injectorId);
      softly
          .assertThat(ids(registered.get("asset_tags")))
          .as("tags in the upsert response")
          .containsExactlyInAnyOrderElementsOf(seededTags);
      upsert(registered);

      // -- Assert --
      softly
          .assertThat(injectorsManaging(platform))
          .as("injector links after upserting the response again")
          .containsExactly(injectorId);
      softly
          .assertThat(tagLinksInDatabase(platform))
          .as("tag links after upserting the response again")
          .containsExactlyInAnyOrderElementsOf(seededTags);
      softly.assertAll();
    }

    @Test
    @DisplayName(
        "given an injector not linked yet should return the link the same upsert creates when"
            + " its platform already exists")
    void given_injectorNotLinkedYet_should_returnTheLinkTheUpsertCreates() throws Exception {
      // -- Arrange --
      String injectorType = "round-trip-injector-" + UUID.randomUUID();
      SecurityPlatform platform = seedPlatform(injectorType);
      String injectorId = seedInjectorManaging(null, injectorType);

      // -- Act --
      JsonNode registered = upsert(registrationOf(platform, tagIds(platform)));

      // -- Assert --
      // The upsert links the injector inside the request: the response must already show it, as
      // it did when the association was loaded after the commit.
      SoftAssertions softly = new SoftAssertions();
      softly
          .assertThat(ids(registered.get("security_platform_injectors")))
          .as("injectors in the upsert response")
          .containsExactly(injectorId);
      softly
          .assertThat(injectorsManaging(platform))
          .as("injector links in the database")
          .containsExactly(injectorId);
      softly.assertAll();
    }

    /** The registration call a collector or an injector sends for this platform. */
    private JsonNode registrationOf(SecurityPlatform platform, List<String> tags) {
      ObjectNode registration =
          mapper
              .createObjectNode()
              .put("asset_name", platform.getName())
              .put("asset_external_reference", platform.getExternalReference())
              .put("security_platform_type", platform.getSecurityPlatformType().name());
      registration.set("asset_tags", mapper.valueToTree(tags));
      return registration;
    }

    private JsonNode upsert(JsonNode body) throws Exception {
      return readJson(
          mvc.perform(
              post(TENANT_SECURITY_PLATFORM_URI + "/upsert", tenant)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(mapper.writeValueAsString(body))
                  .with(csrf())));
    }
  }

  // -- SEEDING --

  /** A platform carrying two tags, registered under {@code externalReference} when not null. */
  private SecurityPlatform seedPlatform(String externalReference) {
    SecurityPlatform platform =
        SecurityPlatformFixture.createDefault(
            "round-trip-platform-" + UUID.randomUUID(),
            SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR.name());
    platform.setTenant(new Tenant(tenant));
    platform.setExternalReference(externalReference);
    return inTenantScope(
        () -> {
          Tag first = newTag().persist().get();
          Tag second = newTag().persist().get();
          platform.setTags(new HashSet<>(List.of(first, second)));
          return securityPlatformComposer.forSecurityPlatform(platform).persist().get();
        });
  }

  /** An injector of {@code injectorType}, linked to {@code platform} when not null. */
  private String seedInjectorManaging(SecurityPlatform platform, String injectorType) {
    Injector injector =
        InjectorFixture.createInjector(
            UUID.randomUUID().toString(), "round-trip-injector", injectorType);
    injector.setTenantId(tenant);
    seededInjectors.add(injector.getId());
    return inTenantScope(
        () -> {
          if (platform != null) {
            injector.setSecurityPlatform(
                securityPlatformRepository.findById(platform.getId()).get());
          }
          return injectorRepository.save(injector).getId();
        });
  }

  private TagComposer.Composer newTag() {
    Tag tag = getTagWithText("security-platform-tag-" + UUID.randomUUID());
    tag.setTenant(new Tenant(tenant));
    return tagComposer.forTag(tag);
  }

  private <T> T inTenantScope(Supplier<T> work) {
    return tenantTx.execute(TxCtx.forTenant(tenant), work);
  }

  // -- CALLS --

  /** Sends the platform back through the update endpoint exactly as a read returned it. */
  private void saveUnchanged(SecurityPlatform platform, JsonNode readPlatform) throws Exception {
    mvc.perform(
            put(TENANT_SECURITY_PLATFORM_URI + "/{securityPlatformId}", tenant, platform.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(readPlatform))
                .with(csrf()))
        .andExpect(status().isOk());
  }

  private JsonNode readJson(ResultActions call) throws Exception {
    return mapper.readTree(
        call.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
  }

  private static JsonNode findById(JsonNode platforms, String platformId) {
    return StreamSupport.stream(platforms.spliterator(), false)
        .filter(node -> platformId.equals(node.get("asset_id").asText()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("platform " + platformId + " not returned"));
  }

  // -- CHECKS --

  private List<String> tagLinksInDatabase(SecurityPlatform platform) {
    return jdbc.queryForList(
        "SELECT tag_id FROM assets_tags WHERE asset_id = ?", String.class, platform.getId());
  }

  private List<String> injectorsManaging(SecurityPlatform platform) {
    return jdbc.queryForList(
        "SELECT injector_id FROM injectors WHERE injector_security_platform = ?",
        String.class,
        platform.getId());
  }

  private static List<String> ids(JsonNode array) {
    return StreamSupport.stream(array.spliterator(), false).map(JsonNode::asText).toList();
  }

  private static List<String> tagIds(SecurityPlatform platform) {
    return platform.getTags().stream().map(Tag::getId).toList();
  }
}
