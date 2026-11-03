package io.openaev.rest.challenge;

import static io.openaev.rest.challenge.ChallengeApi.TENANT_CHALLENGE_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.ChallengeFixture.createDefaultChallenge;
import static io.openaev.utils.fixtures.DocumentFixture.getDocumentJpeg;
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
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Challenge;
import io.openaev.database.model.ChallengeFlag;
import io.openaev.database.model.Document;
import io.openaev.database.model.Tag;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.ChallengeRepository;
import io.openaev.database.repository.DocumentRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.rest.challenge.form.ChallengeInput;
import io.openaev.rest.challenge.form.FlagInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.composers.ChallengeComposer;
import io.openaev.utils.fixtures.composers.DocumentComposer;
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
 * With {@code tags} on v2 tenant isolation, the challenge read endpoints return the raw {@code
 * Challenge} entity and would serialize its lazy {@code challenge_tags} open-in-view, AFTER the
 * controller transaction has committed and {@code app.current_tenants} is cleared: the tag lookup
 * then fails closed to an empty array. The tags showed up right after a create (set in memory) and
 * vanished on the next reload, and the edit form then saved the empty list over the real links.
 * {@link io.openaev.service.ChallengeService} loads the tags inside the scoped transaction to
 * prevent it.
 *
 * <p>The tests cover every lazy association of a challenge (tags, documents, flags) and run with
 * the production list of isolated tables, read from {@code application.properties}: activating
 * another table that one of these associations reaches (for example {@code challenges_flags}) makes
 * them fail until the association is loaded inside the scope too.
 *
 * <p>The class is deliberately NOT {@code @Transactional}: a rolled-back test transaction never
 * commits, so the tenant-local GUC would stay alive through serialization and MASK the very failure
 * this pins. Seeding, checks and cleanup therefore run in committed transactions opened through
 * {@link TenantScopedTransaction}, scoped to the test tenant.
 */
@WithMockUser(isAdmin = true)
@DisplayName("Challenge endpoints serialize every lazy association with the production isolation")
class ChallengeTagsSerializationTenantScopeTest extends IntegrationTest {

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
  @Autowired private ChallengeComposer challengeComposer;
  @Autowired private TagComposer tagComposer;
  @Autowired private DocumentComposer documentComposer;
  @Autowired private ChallengeRepository challengeRepository;
  @Autowired private TagRepository tagRepository;
  @Autowired private DocumentRepository documentRepository;

  private String tenant;

  @BeforeEach
  void setUp() throws Exception {
    challengeComposer.reset();
    tagComposer.reset();
    documentComposer.reset();
    // A tenant the current user is a member of, so the request scope covers the seeded rows.
    tenant = tenantHelper.createTenantWithCurrentUser("challenge-tag-sink").getId();
  }

  @AfterEach
  void cleanup() {
    // The composers' rows were committed: delete them by id in a transaction scoped to the tenant,
    // then the tenant itself. The helper's deletes on collectors and collector_types (isolated in
    // production) join this transaction, so they run with the tenant in scope.
    inTenantScope(
        () -> {
          challengeRepository.deleteAllById(
              challengeComposer.generatedItems.stream().map(Challenge::getId).toList());
          documentRepository.deleteAllById(
              documentComposer.generatedItems.stream().map(Document::getId).toList());
          tagRepository.deleteAllById(tagComposer.generatedItems.stream().map(Tag::getId).toList());
          tenantHelper.deleteCommittedTenants(tenant);
          return null;
        });
  }

  @Nested
  @DisplayName("List challenges")
  class ListChallenges {

    @Test
    @DisplayName(
        "given a challenge carrying every lazy association when the challenges are listed then all"
            + " of them are serialized")
    void
        given_a_challenge_carrying_every_lazy_association_when_the_challenges_are_listed_then_all_of_them_are_serialized()
            throws Exception {
      // -- Arrange --
      Challenge challenge = seedChallengeWithEveryLazyAssociation();

      // -- Act & Assert --
      expectEveryLazyAssociation(listChallenges(), challenge);
    }

    @Test
    @DisplayName(
        "given a challenge carrying tags when the challenges are listed then the tags are"
            + " serialized")
    void
        given_a_challenge_carrying_tags_when_the_challenges_are_listed_then_the_tags_are_serialized()
            throws Exception {
      // -- Arrange --
      Challenge challenge = seedChallenge(c -> c.withTag(newTag()).withTag(newTag()));

      // -- Act & Assert --
      // Without the in-scope load the lazy challenge_tags serializes as [] here: the post-commit
      // open-in-view load runs with app.current_tenants cleared and fails closed.
      listChallenges()
          .andExpect(
              jsonPath("$[0].challenge_tags", containsInAnyOrder(tagIds(challenge).toArray())));
    }

    @Test
    @DisplayName(
        "given a challenge with no tag when the challenges are listed then its tags are genuinely"
            + " empty")
    void given_a_challenge_with_no_tag_when_the_challenges_are_listed_then_its_tags_are_empty()
        throws Exception {
      // -- Arrange --
      seedChallenge(UnaryOperator.identity());

      // -- Act & Assert --
      // Empty here proves the assertion above tracks the real links and is not always non-empty.
      listChallenges().andExpect(jsonPath("$[0].challenge_tags", empty()));
    }

    @Test
    @DisplayName(
        "given a challenge carrying one document and two tags when the challenges are listed then"
            + " the document is serialized once")
    void
        given_a_challenge_carrying_one_document_and_two_tags_when_the_challenges_are_listed_then_the_document_is_serialized_once()
            throws Exception {
      // -- Arrange --
      Challenge challenge =
          seedChallenge(c -> c.withDocument(newDocument()).withTag(newTag()).withTag(newTag()));

      // -- Act & Assert --
      // challenge_documents is a list: loading the tags in the documents query would repeat the
      // document once per tag.
      listChallenges()
          .andExpect(
              jsonPath("$[0].challenge_documents", contains(documentIds(challenge).toArray())));
    }
  }

  @Nested
  @DisplayName("Find challenges by id")
  class FindChallenges {

    @Test
    @DisplayName("given a challenge carrying a tag when found by id then the tag is serialized")
    void given_a_challenge_carrying_a_tag_when_found_by_id_then_the_tag_is_serialized()
        throws Exception {
      // -- Arrange --
      Challenge challenge = seedChallenge(c -> c.withTag(newTag()));

      // -- Act & Assert --
      // POST /challenges/find returns raw Challenge entities too, with the same lazy tags.
      findChallenge(challenge)
          .andExpect(jsonPath("$[0].challenge_tags", contains(tagIds(challenge).toArray())));
    }

    @Test
    @DisplayName(
        "given a challenge carrying every lazy association when found by id then all of them are"
            + " serialized")
    void
        given_a_challenge_carrying_every_lazy_association_when_found_by_id_then_all_of_them_are_serialized()
            throws Exception {
      // -- Arrange --
      Challenge challenge = seedChallengeWithEveryLazyAssociation();

      // -- Act & Assert --
      expectEveryLazyAssociation(findChallenge(challenge), challenge);
    }
  }

  @Nested
  @DisplayName("Edit a challenge")
  class EditChallenge {

    @Test
    @DisplayName(
        "given a challenge carrying every lazy association when a tag is added through the edit"
            + " form then nothing existing is lost")
    void
        given_a_challenge_carrying_every_lazy_association_when_a_tag_is_added_through_the_edit_form_then_nothing_existing_is_lost()
            throws Exception {
      // -- Arrange --
      Challenge challenge = seedChallengeWithEveryLazyAssociation();
      String addedTagId = inTenantScope(() -> newTag().persist().get().getId());
      List<String> editedTags = new ArrayList<>(tagIds(challenge));
      editedTags.add(addedTagId);

      // -- Act --
      // Same flow as the edit form: it is filled from the list response, then the new tag is
      // added. The list must therefore show every existing link: an empty collection here is what
      // erased them before the fix.
      expectEveryLazyAssociation(listChallenges(), challenge);
      mvc.perform(
              put(TENANT_CHALLENGE_URI + "/{challengeId}", tenant, challenge.getId())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      asJsonString(
                          editInput(editedTags, documentIds(challenge), flagValues(challenge))))
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- Assert --
      inTenantScope(
          () -> {
            Challenge saved = challengeRepository.findById(challenge.getId()).orElseThrow();
            assertThat(tagIds(saved)).containsExactlyInAnyOrderElementsOf(editedTags);
            assertThat(documentIds(saved)).containsExactlyElementsOf(documentIds(challenge));
            assertThat(flagValues(saved)).containsExactlyElementsOf(flagValues(challenge));
            return null;
          });
    }

    private ChallengeInput editInput(
        List<String> tagIds, List<String> documentIds, List<String> flagValues) {
      List<FlagInput> flags =
          flagValues.stream()
              .map(
                  value -> {
                    FlagInput flag = new FlagInput();
                    flag.setType("VALUE");
                    flag.setValue(value);
                    return flag;
                  })
              .toList();
      return new ChallengeInput(
          "edited-challenge-" + UUID.randomUUID(),
          null,
          null,
          null,
          null,
          tagIds,
          documentIds,
          flags);
    }
  }

  // -- SEEDING --

  /** A challenge with one tag, one document and the fixture's flag: every lazy association. */
  private Challenge seedChallengeWithEveryLazyAssociation() {
    return seedChallenge(c -> c.withTag(newTag()).withDocument(newDocument()));
  }

  private Challenge seedChallenge(UnaryOperator<ChallengeComposer.Composer> withLinks) {
    Challenge challenge = createDefaultChallenge();
    challenge.setTenant(new Tenant(tenant));
    return inTenantScope(
        () -> withLinks.apply(challengeComposer.forChallenge(challenge)).persist().get());
  }

  private TagComposer.Composer newTag() {
    Tag tag = getTagWithText("challenge-tag-" + UUID.randomUUID());
    tag.setTenant(new Tenant(tenant));
    return tagComposer.forTag(tag);
  }

  private DocumentComposer.Composer newDocument() {
    Document document = getDocumentJpeg();
    document.setName("challenge-document-" + UUID.randomUUID());
    document.setTarget(document.getName());
    document.setTenant(new Tenant(tenant));
    return documentComposer.forDocument(document);
  }

  /** Runs the work in a committed transaction scoped to the test tenant. */
  private <T> T inTenantScope(Supplier<T> work) {
    return tenantTx.execute(TxCtx.forTenant(tenant), work);
  }

  // -- CALLS AND ASSERTIONS --

  /** Lists the challenges under the test tenant's path: the fresh tenant holds only one. */
  private ResultActions listChallenges() throws Exception {
    return mvc.perform(
            get(TENANT_CHALLENGE_URI, tenant).accept(MediaType.APPLICATION_JSON).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)));
  }

  private ResultActions findChallenge(Challenge challenge) throws Exception {
    return mvc.perform(
            post(TENANT_CHALLENGE_URI + "/find", tenant)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(List.of(challenge.getId())))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)));
  }

  private static void expectEveryLazyAssociation(ResultActions response, Challenge challenge)
      throws Exception {
    response
        .andExpect(jsonPath("$[0].challenge_tags", containsInAnyOrder(tagIds(challenge).toArray())))
        .andExpect(jsonPath("$[0].challenge_documents", contains(documentIds(challenge).toArray())))
        .andExpect(
            jsonPath("$[0].challenge_flags[*].flag_id", contains(flagIds(challenge).toArray())));
  }

  private static List<String> tagIds(Challenge challenge) {
    return challenge.getTags().stream().map(Tag::getId).toList();
  }

  private static List<String> documentIds(Challenge challenge) {
    return challenge.getDocuments().stream().map(Document::getId).toList();
  }

  private static List<String> flagIds(Challenge challenge) {
    return challenge.getFlags().stream().map(ChallengeFlag::getId).toList();
  }

  private static List<String> flagValues(Challenge challenge) {
    return challenge.getFlags().stream().map(ChallengeFlag::getValue).toList();
  }
}
